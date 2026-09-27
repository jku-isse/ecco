package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.view.artifacts.AssociationInfoImpl;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The association previews of the TypeScript and Python adapters (SourceSpanViewer), as the GUI
 * gets them from the injector: a file of two commits under different features shows each part in
 * its association's color. Each preview is also saved as build/association-preview-*.png.
 */
public class SourceSpanViewerTest {

    private static final Color FIRST = Color.web("#ffb3b3"), SECOND = Color.web("#b3e6b3");

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    private static boolean pythonAvailable() {
        try {
            Process process = new ProcessBuilder("python", "-c", "import libcst, py4j").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    @Timeout(180)
    public void typeScriptPreviewColorsBothAssociations() throws Exception {
        preview("main.ts",
                "let x: number = 1;\nfunction f(a: string) {\n  return a;\n}\n",
                "let x: number = 1;\nfunction f(a: string) {\n  console.log(a);\n  return a;\n}\nlet y = 2;\n",
                "at.jku.isse.ecco.adapter.typescript.TypeScriptPlugin", "typescript");
    }

    @Test
    @Timeout(180)
    public void pythonPreviewColorsBothAssociations() throws Exception {
        assumeTrue(pythonAvailable(), "needs `python` with libcst and py4j on the PATH");
        preview("main.py",
                "x = 1\n\ndef f(a):\n    return a\n",
                "x = 1\n\ndef f(a):\n    print(a)\n    return a\n\ny = 2\n",
                "at.jku.isse.ecco.adapter.python.PythonPlugin", "python");
    }

    private void preview(String file, String first, String second, String pluginId, String name) throws Exception {
        Path workDir = Files.createTempDirectory("source-span-viewer");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve(file), first);
            service.commit("first", "A");
            Files.writeString(content.resolve(file), second);
            service.commit("second", "A, B");

            AssociationInfoArtifactViewer viewer = service.getInjector()
                    .getInstance(Key.get(new TypeLiteral<Set<AssociationInfoArtifactViewer>>() {
                    })).stream().filter(v -> v.getPluginId().equals(pluginId)).findFirst()
                    .orElseThrow(() -> new AssertionError("no preview registered for " + pluginId));

            // all associations composed into one tree, like the Artifacts view does
            List<AssociationInfo> infos = new ArrayList<>();
            LazyCompositionRootNode root = new LazyCompositionRootNode();
            int i = 0;
            for (Association association : service.getRepository().getAssociations()) {
                root.addOrigNode(association.getRootNode());
                AssociationInfoImpl info = new AssociationInfoImpl(association);
                info.setSelected(true);
                info.colorProperty().set(i++ % 2 == 0 ? FIRST : SECOND);
                infos.add(info);
            }
            Node fileNode = find(root, file);
            assertNotNull(fileNode, file + " not in the composed tree");

            CompletableFuture<List<Color>> colors = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    viewer.setAssociationInfos(infos);
                    viewer.showTree(fileNode);
                    Stage stage = new Stage();
                    stage.setScene(new Scene((Pane) viewer, 700, 260));
                    stage.show();
                    ((Pane) viewer).applyCss();
                    ((Pane) viewer).layout();
                    List<Color> found = new ArrayList<>();
                    for (javafx.scene.Node label : ((Pane) viewer).lookupAll(".label"))
                        if (label instanceof Label l && l.getBackground() != null && !l.getBackground().getFills().isEmpty()
                                && l.getBackground().getFills().get(0).getFill() instanceof Color c)
                            found.add(c);
                    save(((Pane) viewer).snapshot(null, null), Paths.get("build", "association-preview-" + name + ".png"));
                    stage.close();
                    colors.complete(found);
                } catch (Throwable t) {
                    colors.completeExceptionally(t);
                }
            });
            List<Color> found = colors.get(60, TimeUnit.SECONDS);
            assertTrue(found.contains(FIRST) && found.contains(SECOND), "both associations' colors must show, found " + found);
        }
    }

    private static Node find(Node node, String file) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data
                && data.getPath().toString().equals(file))
            return node;
        for (Node child : node.getChildren()) {
            Node found = find(child, file);
            if (found != null)
                return found;
        }
        return null;
    }

    private static void save(WritableImage image, Path path) throws IOException {
        BufferedImage buffered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < buffered.getHeight(); y++)
            for (int x = 0; x < buffered.getWidth(); x++)
                buffered.setRGB(x, y, pixels.getArgb(x, y));
        Files.createDirectories(path.getParent());
        ImageIO.write(buffered, "png", path.toFile());
    }
}
