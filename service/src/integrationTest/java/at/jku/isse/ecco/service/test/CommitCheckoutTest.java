package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static at.jku.isse.ecco.util.directory.DirectoryUtils.deleteFolderIfItExists;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class CommitCheckoutTest {

    private static final Path REPO_PATH;
    private static final Path CHECKOUT_PATH;

    static {
        try {
            REPO_PATH = ResourceUtils.getResourceFolderPath("repo").resolve(".ecco");
            CHECKOUT_PATH = ResourceUtils.getResourceFolderPath("checkout");
        } catch (ResourceException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    @AfterAll
    public static void deleteRepository() throws DirectoryException {
        deleteFolderIfItExists(REPO_PATH);
    }

    @BeforeAll
    public static void deleteGitKeepFile() throws IOException {
        Path gitKeepFilePath = CHECKOUT_PATH.resolve(".gitkeep");
        if (Files.exists(gitKeepFilePath)){
            Files.delete(gitKeepFilePath);
        }
    }

    @AfterAll
    public static void recreateGitKeepFile() throws IOException {
        Path gitKeepFilePath = CHECKOUT_PATH.resolve(".gitkeep");
        if (!Files.exists(gitKeepFilePath)){
            Files.createFile(gitKeepFilePath);
        }
    }

    @AfterEach
    public void deleteAndCreateCheckout() throws DirectoryException {
        DirectoryUtils.deleteAndCreateFolder(CHECKOUT_PATH);
    }

    @Test
    public void commitAndCheckoutProductTest() throws ResourceException, DirectoryException {
        try (EccoService eccoService = new EccoService()) {
            Path repositoryPath = ResourceUtils.getResourceFolderPath("repo");
            eccoService.setRepositoryDir(repositoryPath.resolve(".ecco"));
            eccoService.init();

            Path variant1Path = ResourceUtils.getResourceFolderPath("input/V1");
            Path variant2Path = ResourceUtils.getResourceFolderPath("input/V2");
            eccoService.setBaseDir(variant1Path);
            eccoService.commit();
            eccoService.setBaseDir(variant2Path);
            eccoService.commit();

            Path checkoutFile = CHECKOUT_PATH.resolve("file.txt");
            eccoService.setBaseDir(CHECKOUT_PATH);

            eccoService.checkout("A.0");
            Path variant1File = variant1Path.resolve("file.txt");
            assertEquals(-1, Files.mismatch(variant1File, checkoutFile));

            DirectoryUtils.deleteAndCreateFolder(CHECKOUT_PATH);

            eccoService.checkout("A.1, B.1");
            Path variant2File = variant2Path.resolve("file.txt");
            assertEquals(-1, Files.mismatch(variant2File, checkoutFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void serializeAndDeserializeTest()throws ResourceException, DirectoryException {
        Path repositoryPath = ResourceUtils.getResourceFolderPath("repo");
        Path variant1Path = ResourceUtils.getResourceFolderPath("input/V1");
        Path variant2Path = ResourceUtils.getResourceFolderPath("input/V2");

        try (EccoService eccoService = new EccoService()) {
            eccoService.setRepositoryDir(repositoryPath.resolve(".ecco"));
            eccoService.init();
            eccoService.setBaseDir(variant1Path);
            eccoService.commit();
            eccoService.setBaseDir(variant2Path);
            eccoService.commit();
        }

        try (EccoService eccoService = new EccoService()) {
            eccoService.setRepositoryDir(repositoryPath.resolve(".ecco"));
            eccoService.open();
            Path checkoutFile = CHECKOUT_PATH.resolve("file.txt");
            eccoService.setBaseDir(CHECKOUT_PATH);
            eccoService.checkout("A.0");
            Path variant1File = variant1Path.resolve("file.txt");
            assertEquals(-1, Files.mismatch(variant1File, checkoutFile));
            DirectoryUtils.deleteAndCreateFolder(CHECKOUT_PATH);
            eccoService.checkout("A.1, B.1");
            Path variant2File = variant2Path.resolve("file.txt");
            assertEquals(-1, Files.mismatch(variant2File, checkoutFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
