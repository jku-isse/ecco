package at.jku.isse.ecco.storage.xml.test;

import at.jku.isse.ecco.storage.xml.XmlPlugin;
import at.jku.isse.ecco.storage.xml.impl.XmlRepository;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class SimpleTest {

    private static final Path resourceRoot = Paths.get("src/integrationTest/data/out");

    @BeforeClass
    public static void prepare() throws IOException {
        Files.createDirectories(resourceRoot);
    }

    @Test
    public void readWriteRepo() throws IOException {
        XmlPlugin plugin = new XmlPlugin();
        Path repoFile = resourceRoot.resolve("ecco.db.xml");
        Files.deleteIfExists(repoFile);
        XmlRepository repository = new XmlRepository();
        repository.storeRepo(repoFile);

        final XmlRepository jsonRepository = XmlRepository.loadFromDisk(repoFile);

        Objects.requireNonNull(jsonRepository);
        System.out.println("Using: " + plugin.getName());
    }
}
