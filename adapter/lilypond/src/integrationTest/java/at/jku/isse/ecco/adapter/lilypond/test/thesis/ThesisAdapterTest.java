package at.jku.isse.ecco.adapter.lilypond.test.thesis;

import at.jku.isse.ecco.adapter.lilypond.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
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
