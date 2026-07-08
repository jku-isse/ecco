package at.jku.isse.ecco.adapter.lilypond.test.thesis;

import at.jku.isse.ecco.adapter.lilypond.*;
import at.jku.isse.ecco.adapter.lilypond.parce.LilypondNodeSerializationWrapper;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.adapter.lilypond.test.FeatureFileVisitor;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
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

public class ThesisAdapterTest {

	private static final Path DATA_DIR;
	private static final Path BASE_DIR;

	static {
        Path data = null;
        try {
            data = Paths.get(Objects.requireNonNull(ThesisAdapterTest.class.getClassLoader().getResource("data")).toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        DATA_DIR = data;

        Path startupPath = Path.of(System.getProperty("user.dir"));
        BASE_DIR = startupPath.getParent().getParent();

        assert DATA_DIR != null;
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

    private static final Path[] SULZER_FILE = new Path[]{Paths.get("input/factusestrepente.ly")};
    /**
     * input/factusestrepente.ly was never checked in as a fixture (a LilyPond music-notation
     * file - not something to fabricate without domain knowledge). Reading a missing file doesn't
     * fail cleanly here either: it surfaces downstream as EmptyStackException in
     * LilypondWriter.ArtifactIterator, a separate bug worth a real fixture and investigation, not
     * a guess.
     */
    @Test(groups = {"parce"})
    @Ignore
    public void SulzerWriterTest() {
        LilypondReader rd = new LilypondReader((new SerEntityFactory()));
        Set<Node.Op> nodes = rd.read(DATA_DIR, SULZER_FILE);

        System.out.println("WRITE");
        LilypondStringWriter lsw = new LilypondStringWriter();
        for (String s : lsw.write(nodes.stream().map(op -> (Node)op).collect(Collectors.toSet()))) {
            System.out.println(s);
        }
        System.out.println("END WRITE");
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
