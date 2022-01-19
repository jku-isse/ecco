package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.lilypond.*;
import at.jku.isse.ecco.adapter.lilypond.parce.LilypondNodeSerializationWrapper;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdapterTest {

	private static final Path DATA_DIR;
	private static final Path BASE_DIR;
    private static final Path[] FILES;

	static {
        Path data = null;
        try {
            data = Paths.get(Objects.requireNonNull(AdapterTest.class.getClassLoader().getResource("data")).toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        DATA_DIR = data;

        Path startupPath = Path.of(System.getProperty("user.dir"));
        BASE_DIR = startupPath.getParent().getParent();

        assert DATA_DIR != null;
        FILES = new Path[]{
                DATA_DIR.resolve("input/v1_notes/lily.ly"),
                DATA_DIR.resolve("input/v2_notesslurs/lily.ly"),
                DATA_DIR.resolve("input/v3_notesdynamics/lily.ly"),
                DATA_DIR.resolve("input/v4_notesaccents/lily.ly"),
        };
    }

    @Test(groups = {"parce"})
    @BeforeClass(alwaysRun = true)
    private void setUp() {
        Logger log = LilypondReader.getLogger();
        log.setLevel(Level.ALL);
        try {
            FileHandler handler = new FileHandler("test.log");
            handler.setFormatter(new SimpleFormatter());
            log.addHandler(handler);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test(groups = {"parce"})
	public void Lilypond_Adapter_Test() {
		LilypondReader reader = new LilypondReader(new MemEntityFactory());

		System.out.println("READ FILES");
		Set<Node.Op> nodes = reader.read(FILES);
        Assert.assertEquals(nodes.size(), FILES.length);

		System.out.println("END READ (" + nodes.size() + " files)");

        System.out.println("DUMP TREES");
        LilypondStringWriter sw = new LilypondStringWriter();
        String[] out = sw.write(nodes.stream().map(op -> (Node)op).collect(Collectors.toSet()));
        Assert.assertEquals(out.length, FILES.length);
        Assert.assertTrue(out[0].length() > 0);
        System.out.println(String.join("\n-------------------------\n", out));
        System.out.println("END DUMP TREES");
	}

    private static final Path[] SIMPLE_FILES = new Path[]{Paths.get("input/simple.ly")};
	@Test()
	public void SimpleLilypond() {
	    LilypondWhitespaceReader rd = new LilypondWhitespaceReader((new MemEntityFactory()));

	    System.out.println("READ");
	    Set<Node> nodes = rd.read(DATA_DIR, SIMPLE_FILES);

	    nodes.iterator().next().getChildren().forEach(System.out::println);
    }

    @Test()
    public void SimpleWriterTest() {
        LilypondWhitespaceReader rd = new LilypondWhitespaceReader((new MemEntityFactory()));
        Set<Node> nodes = rd.read(DATA_DIR, SIMPLE_FILES);

	    System.out.println("WRITE");
        LilypondStringWriter lsw = new LilypondStringWriter();
        for (String s : lsw.write(nodes)) {
            System.out.println(s);
        }
        System.out.println("END WRITE");
    }

    private static final Path[] DIEU_FILE = new Path[]{Paths.get("input/dieu.ly")};
    @Test(groups = {"parce"})
    public void DieuWriterTest() {
        LilypondReader rd = new LilypondReader((new MemEntityFactory()));
        Set<Node.Op> nodes = rd.read(DATA_DIR, DIEU_FILE);

        System.out.println("WRITE");
        LilypondStringWriter lsw = new LilypondStringWriter();
        for (String s : lsw.write(nodes.stream().map(op -> (Node)op).collect(Collectors.toSet()))) {
            System.out.println(s);
        }
        System.out.println("END WRITE");
    }

    private static final Path[] SULZER_FILE = new Path[]{Paths.get("input/factusestrepente.ly")};
    @Test(groups = {"parce"})
    public void SulzerWriterTest() {
        LilypondReader rd = new LilypondReader((new MemEntityFactory()));
        Set<Node.Op> nodes = rd.read(DATA_DIR, SULZER_FILE);

        System.out.println("WRITE");
        LilypondStringWriter lsw = new LilypondStringWriter();
        for (String s : lsw.write(nodes.stream().map(op -> (Node)op).collect(Collectors.toSet()))) {
            System.out.println(s);
        }
        System.out.println("END WRITE");
    }

    @DataProvider(name = "serializationPaths")
    public static Object[][] serializedNodesPaths() {
        // input and output path relative to the parent directory (e.g. ../ecco)
        return new String[][] {
                {"lytests/debussy", "lytests/dieu_nodes"}
                //{"lytests/sulzer", "lytests/sulzer_nodes"}
        };
    }

    /**
     * This method can be used to serialize parser input such that {@link LilypondParser} does not depend on Parce
     * which needs the Py4J module to be set up.
     * @throws IOException Thrown if feature path is missing.
     */
    @Test(groups = {"parce"}, dataProvider = "serializationPaths")
    public void serializeParsedNodes(String inputDir, String outputDir) throws IOException {
        ParserFactory.setParseFiles(true);
        Path featuresPath = BASE_DIR.getParent().resolve(inputDir);

        if (!Files.exists(featuresPath)) {
            throw new IOException("feature path " + featuresPath.toAbsolutePath() + " does not exist");
        }

        Path serPath = BASE_DIR.getParent().resolve(outputDir);
        if (!Files.exists(serPath)) {
            // create subdirectories if needed
            Files.createDirectories(serPath);
            System.out.println("created directory '" + serPath.toAbsolutePath() + "'");
        }

        deleteDirectoryContents(serPath);
        Files.createDirectory(serPath);

        LilypondParser<ParceToken> parser = ParserFactory.getParser();
        try {
            parser.init();

            Files.walkFileTree(featuresPath, new FeatureFileVisitor(serPath, p -> {
                LilypondNode<ParceToken> head = parser.parse(p);

                final Path dest = serPath.resolve(featuresPath.relativize(p));
                try (FileOutputStream fos = new FileOutputStream(dest.toFile());
                     ObjectOutputStream oos = new ObjectOutputStream(fos)) {

                    LilypondNode<ParceToken> n = head;
                    int nNodes = 0;
                    while (n != null) {
                        nNodes++;
                        n = n.getNext();
                    }
                    n = head;

                    oos.writeInt(nNodes);

                    while (n != null) {
                        oos.writeObject(new LilypondNodeSerializationWrapper(n));
                        n = n.getNext();
                    }
                    oos.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));

        } finally {
            parser.shutdown();
        }
    }

    static void deleteDirectoryContents(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    //.peek(System.out::println)
                    .forEach(File::delete);
        }
    }
}
