package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.adapter.AdapterUnavailableException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.file.FileReader;
import at.jku.isse.ecco.adapter.text.TextReader;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.CommitCancelledException;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A file its adapter cannot read is read by another one instead of failing the commit: a Git LFS
 * pointer named .png (text, which the image adapter cannot decode), a .png that is no image at all,
 * a .json with comments (the Python adapter's JSON reader rejects them). Each one used to make
 * every commit of its folder fail.
 */
public class DispatchReaderFallbackTest {

    private static final String LFS_POINTER = "version https://git-lfs.github.com/spec/v1\n"
            + "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n"
            + "size 12345\n";

    @Test
    @Timeout(120)
    public void aFileItsAdapterCannotReadIsCommittedAndCheckedOutAsItIs(@TempDir Path tmp) throws Exception {
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("pointer.png"), LFS_POINTER);
        Files.write(content.resolve("broken.png"), new byte[] {0, 1, 2, 3, 0, 'x'});
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xff0000);
        ImageIO.write(image, "png", content.resolve("real.png").toFile());
        Files.writeString(content.resolve("notes.txt"), "one\ntwo\n");

        Path out = Files.createDirectories(tmp.resolve("out"));
        Map<String, String> pluginIds = new HashMap<>();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            service.commit("all", "A");

            service.setBaseDir(out);
            Checkout checkout = service.checkout("A");
            collectPluginIds(checkout.getNode(), pluginIds);
        }

        for (String file : List.of("pointer.png", "broken.png", "notes.txt"))
            assertArrayEquals(Files.readAllBytes(content.resolve(file)), Files.readAllBytes(out.resolve(file)), file);
        // the image adapter keeps pixels, not bytes
        BufferedImage written = ImageIO.read(out.resolve("real.png").toFile());
        assertEquals(0xff0000, written.getRGB(1, 1) & 0xffffff);
        assertEquals(0, written.getRGB(0, 0) & 0xffffff);
        // the image adapter still reads the image beside them; the others are read by text or as a whole
        assertEquals("at.jku.isse.ecco.adapter.image.ImagePlugin", pluginIds.get("real.png"));
        assertEquals(DispatchReader.TEXT_PLUGIN_ID, pluginIds.get("pointer.png"));
        assertEquals(DispatchReader.FILE_PLUGIN_ID, pluginIds.get("broken.png"));
    }

    /** As the Python adapter fails on a .json with comments: only that file of its batch is read otherwise. */
    @Test
    public void ofABatch_onlyTheFileTheAdapterCannotReadIsReadByAnother(@TempDir Path tmp) throws Exception {
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("good.json"), "{\"a\": 1}\n");
        Files.writeString(content.resolve("bad.json"), "{\n    // a comment\n    \"a\": 1,\n}\n");
        Map<String, String> pluginIds = new HashMap<>();
        for (Node.Op node : reader(tmp, null).read(content, new Path[]{Path.of(".")}))
            collectPluginIds(node, pluginIds);
        assertEquals("json", pluginIds.get("good.json"));
        assertEquals(DispatchReader.TEXT_PLUGIN_ID, pluginIds.get("bad.json"));
    }

    @Test
    public void aCancelledCommitIsNotReadByAnotherAdapter(@TempDir Path tmp) throws Exception {
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("bad.json"), "{}\n");
        DispatchReader reader = reader(tmp, new CommitCancelledException());
        assertThrows(CommitCancelledException.class, () -> reader.read(content, new Path[]{Path.of(".")}));
    }

    /** No Python, or a repository made for another version of the adapter: other adapters would store its files otherwise than their history. */
    @Test
    public void anAdapterThatCannotRunHereFailsTheCommit(@TempDir Path tmp) throws Exception {
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("bad.json"), "{}\n");
        DispatchReader reader = reader(tmp, new IllegalStateException("could not initialize parser",
                new AdapterUnavailableException("No Python with the modules libcst and py4j found")));
        assertThrows(IllegalStateException.class, () -> reader.read(content, new Path[]{Path.of(".")}));
    }

    /** A dispatcher whose adapter for .json fails on files named bad*, or with [error] on every file. */
    private static DispatchReader reader(Path tmp, RuntimeException error) throws Exception {
        SerEntityFactory entityFactory = new SerEntityFactory();
        ArtifactReader<Path, Set<Node.Op>> json = new ArtifactReader<>() {
            @Override
            public String getPluginId() {
                return "json";
            }

            @Override
            public Map<Integer, String[]> getPrioritizedPatterns() {
                return Map.of(Integer.MAX_VALUE, new String[]{"**.json"});
            }

            @Override
            public Set<Node.Op> read(Path base, Path[] input) {
                Set<Node.Op> nodes = new HashSet<>();
                for (Path file : input) {
                    if (error != null)
                        throw error;
                    if (file.getFileName().toString().startsWith("bad"))
                        throw new IllegalStateException("Expecting property name enclosed in double quotes");
                    nodes.add(entityFactory.createOrderedNode(entityFactory.createArtifact(new PluginArtifactData("json", file))));
                }
                return nodes;
            }

            @Override
            public Set<Node.Op> read(Path[] input) {
                return this.read(Path.of("."), input);
            }

            @Override
            public void addListener(ReadListener listener) {
            }

            @Override
            public void removeListener(ReadListener listener) {
            }
        };
        Path repositoryDir = Files.createDirectories(tmp.resolve(".ecco"));
        DispatchReader reader = new DispatchReader(entityFactory,
                Set.of(json, new TextReader(entityFactory), new FileReader(entityFactory)), repositoryDir);
        reader.init();
        return reader;
    }

    private static void collectPluginIds(Node node, Map<String, String> pluginIds) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data)
            pluginIds.put(data.getPath().getFileName().toString(), data.getPluginId());
        for (Node child : node.getChildren())
            collectPluginIds(child, pluginIds);
    }
}
