package at.jku.isse.ecco.storage.xml.test;

import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.xml.impl.XmlRepository;
import at.jku.isse.ecco.storage.xml.impl.XmlRepositoryDao;
import at.jku.isse.ecco.storage.xml.impl.XmlTransactionStrategy;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SimpleTest {

    private static final Path resourceRoot = Paths.get("src/integrationTest/data/out");

    @BeforeClass
    public static void prepare() throws IOException {
        Files.createDirectories(resourceRoot);
    }

    @Test
    public void readWriteRepo() throws IOException {
        Path repoFile = resourceRoot.resolve("ecco.db.xml.zip");
        Files.deleteIfExists(repoFile);
        XmlRepositoryDao repositoryDao = new XmlRepositoryDao(new XmlTransactionStrategy(resourceRoot), new MemEntityFactory());
        XmlRepository a = (XmlRepository) repositoryDao.load();
        repositoryDao.store(a);
        XmlRepository b = (XmlRepository) repositoryDao.load();
        boolean correct = a == b; //Same instance
        if (!correct)
            throw new IllegalStateException("The 2 repositories are not the same!");
        //And once more
        repositoryDao.store(b);
        final XmlRepository c = (XmlRepository) repositoryDao.load();
        correct = c == b; //Same instance
        if (!correct)
            throw new IllegalStateException("The 2 repositories are not the same!");
    }
}
