package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.common.dao.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** The core records the storage format it was written in; a repository from a newer format is refused, saying so. */
public class StorageFormatTest {

    @Test
    @Timeout(60)
    public void aCoreRecordsItsFormatAndANewerOneIsRefused(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve(".ecco");
        Path content = Files.createDirectories(tmp.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            Files.writeString(content.resolve("f.txt"), "a\n");
            service.setBaseDir(content);
            service.commit("one", "A");
        }
        Path core = repo.resolve(Files.readString(repo.resolve("id")).trim() + ".ser.zip");
        Database database = read(core);
        assertEquals(Database.STORAGE_FORMAT, database.getStorageFormat());

        Field format = Database.class.getDeclaredField("storageFormat");
        format.setAccessible(true);
        format.setInt(database, Database.STORAGE_FORMAT + 1);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(core))) {
            zip.putNextEntry(new ZipEntry("ecco.ser"));
            ObjectOutputStream out = new ObjectOutputStream(zip);
            out.writeObject(database);
            out.flush();
            zip.closeEntry();
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            EccoException e = assertThrows(EccoException.class, () -> {
                service.open();
                service.getRepository();
            });
            StringBuilder messages = new StringBuilder();
            for (Throwable t = e; t != null; t = t.getCause())
                messages.append(t.getMessage()).append(" | ");
            assertTrue(messages.toString().contains("newer version of ECCO"), messages.toString());
        }
    }

    private static Database read(Path core) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(core))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                if (entry.getName().equals("ecco.ser"))
                    return (Database) new ObjectInputStream(new ByteArrayInputStream(zip.readAllBytes())).readObject();
        }
        throw new AssertionError("no ecco.ser in " + core);
    }
}
