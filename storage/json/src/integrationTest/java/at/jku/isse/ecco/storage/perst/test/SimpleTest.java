package at.jku.isse.ecco.storage.perst.test;

import at.jku.isse.ecco.storage.json.JsonPlugin;
import at.jku.isse.ecco.storage.json.impl.JsonRepository;
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
        JsonPlugin plugin = new JsonPlugin();
        Path repoFile = resourceRoot.resolve("ecco.db.json.zip");
        Files.deleteIfExists(repoFile);
        JsonRepository repository = new JsonRepository();
        repository.storeRepo(repoFile);

        final JsonRepository jsonRepository = JsonRepository.loadFromDisk(repoFile);

        Objects.requireNonNull(jsonRepository);
        System.out.println("Using: " + plugin.getName());
    }
}
