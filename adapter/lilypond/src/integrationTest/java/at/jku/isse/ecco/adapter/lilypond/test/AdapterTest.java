package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.lilypond.*;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;


public class AdapterTest {

	private static final Path DATA_DIR;
	private static final Path BASE_DIR;
    private static final Path[] FILES;

	static {
        Path data = null;
        try {
            data = Paths.get(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("data")).toURI());
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

    @Test(groups = {"python"})
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

    @Test(groups = {"python"})
	public void Lilypond_Adapter_Test() {
		LilypondReader reader = new LilypondReader(new SerEntityFactory());

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
	public void testWhiteSpaceReader() {
	    LilypondWhitespaceReader rd = new LilypondWhitespaceReader((new SerEntityFactory()));

	    System.out.println("READ");
	    Set<Node> nodes = rd.read(DATA_DIR, SIMPLE_FILES);

	    nodes.iterator().next().getChildren().forEach(System.out::println);
    }

    @Test()
    public void testWhiteSpaceReaderAndWriter() {
        LilypondWhitespaceReader rd = new LilypondWhitespaceReader((new SerEntityFactory()));
        Set<Node> nodes = rd.read(DATA_DIR, SIMPLE_FILES);

	    System.out.println("WRITE");
        LilypondStringWriter lsw = new LilypondStringWriter();
        for (String s : lsw.write(nodes)) {
            System.out.println(s);
        }
        System.out.println("END WRITE");
    }

    private static final Path[] CHANSON_FILE = new Path[]{Paths.get("input/chanson.ly")};
    @Test(groups = {"python"})
    public void ChansonWriterTest() {
        LilypondReader rd = new LilypondReader((new SerEntityFactory()));
        Set<Node.Op> nodes = rd.read(DATA_DIR, CHANSON_FILE);

        System.out.println("WRITE");
        LilypondStringWriter lsw = new LilypondStringWriter();
        for (String s : lsw.write(nodes.stream().map(op -> (Node)op).collect(Collectors.toSet()))) {
            System.out.println(s);
        }
        System.out.println("END WRITE");
    }
}
