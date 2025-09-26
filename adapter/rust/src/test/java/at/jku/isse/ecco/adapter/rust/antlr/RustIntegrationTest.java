package at.jku.isse.ecco.adapter.rust.antlr;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class RustIntegrationTest {
    final Path testDir = Paths.get("src/test/resources/rust_examples/test_output");
    EccoService service;

    @BeforeEach
    public void setUpEcco() {
        try {
            if (!Files.exists(testDir)) {
                Files.createDirectories(testDir);
            }
            service = new EccoService(testDir);
            service.setEntityFactory(new MemEntityFactory());
            service.init();
        } catch (Exception e) {
            System.out.println("Exception during ECCO setup: " + e.getMessage());
        }
    }

    @AfterEach
    public void cleanUp() throws Exception {
        service.close();
        if (Files.exists(testDir)) {
            // Delete files before directories
            try (Stream<Path> walk = Files.walk(testDir)) {
                walk.sorted((a, b) -> b.compareTo(a)) // delete children before parents
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                System.err.println("Failed to delete: " + path);
                            }
                        });
            }
        }
        service = null;
    }

    public void assertFilesEqual(Path excepted, Path actual) throws Exception {
        if (!Files.exists(excepted)) throw new IllegalArgumentException("File does not exist: " + excepted);
        if (!Files.exists(actual)) throw new IllegalArgumentException("File does not exist: " + actual);

        StringBuilder diffReport = new StringBuilder();
        boolean filesAreEqual = true;

        List<String> expectedLines = Files.readAllLines(excepted);
        List<String> actualLines = Files.readAllLines(actual);
        int maxLines = Math.max(expectedLines.size(), actualLines.size());
        for (int i = 0; i < maxLines; i++) {
            String expectedLine = i < expectedLines.size() ? expectedLines.get(i).trim() : "<no line>";
            String actualLine = i < actualLines.size() ? actualLines.get(i).trim() : "<no line>";
            if (!expectedLine.equals(actualLine)) {
                filesAreEqual = false;
                diffReport.append(String.format("Line %d differs:\nExpected: %s\nActual:   %s\n\n", i + 1, expectedLine, actualLine));
            }
        }
        if (!filesAreEqual)  Assertions.fail("Files are not equal:\n" + diffReport);
    }

    public void commitSingleDir(Path dir, EccoService service) {
            service.setBaseDir(dir);
            String version = dir.getFileName().toString();
        try {
            service.commit(version);
        } catch (Exception e) {
            System.out.println("Exception during commit of dir " + dir + ": " + e.getMessage());
        }
        service.setBaseDir(this.testDir);
    }

    @Test
    public void comments() throws Exception {
            Path testFolder = Paths.get("src/test/resources/rust_examples/commentTest/");
            commitSingleDir(testFolder, service);
        try {
            service.checkout("comments.1");
        } catch (Exception e) {
            System.out.println("Exception during checkout: " + e.getMessage());
        }
        Path actual = Paths.get("src/test/resources/rust_examples/commentTest/main.rs");
        Path testOutput = Paths.get("src/test/resources/rust_examples/test_output/main.rs");
        assertFilesEqual(actual, testOutput);
    }

    @Test
    public void functionWithOuterAttribute() throws Exception {
        // Try catch for could not compute hash for file.rs
            String[] folders = { "v1", "v2"};
            String testFolderStr = "src/test/resources/rust_examples/functionTest/";
            commit(folders, testFolderStr, service);
        try {
            service.checkout("hello.1,farewell.1");
        } catch (Exception e) {
            System.out.println("Exception during checkout: " + e.getMessage());
        }
        Path actual = Paths.get("src/test/resources/rust_examples/functionTest/result/main.rs");
        Path testOutput = Paths.get("src/test/resources/rust_examples/test_output/main.rs");
        assertFilesEqual(actual, testOutput);
    }

    @Test
    public void struct() throws Exception {
        Path testFolder = Paths.get("src/test/resources/rust_examples/structTest/");
        commitSingleDir(testFolder, service);
        try {
            service.checkout("struct.1");
        } catch (Exception e) {
            System.out.println("Exception during checkout: " + e.getMessage());
        }
        Path actual = Paths.get("src/test/resources/rust_examples/structTest/main.rs");
        Path testOutput = Paths.get("src/test/resources/rust_examples/test_output/main.rs");
        assertFilesEqual(actual, testOutput);
    }

    @Test
    public void MergeEnumTest() throws Exception {
        String[] folders = { "v1", "v2"};
        String testFolderStr = "src/test/resources/rust_examples/MergeEnumTest/";
        commit(folders, testFolderStr, service);
        try {
            service.checkout("create.1,get.1,getAll.1,change.1");
        } catch (Exception e) {
            System.out.println("Exception during checkout: " + e.getMessage());
        }
        Path actual = Paths.get("src/test/resources/rust_examples/MergeEnumTest/result/main.rs");
        Path testOutput = Paths.get("src/test/resources/rust_examples/test_output/main.rs");
        assertFilesEqual(actual, testOutput);
    }

    @Test
    public void application() throws  Exception {
        try {
            String[] folders = { "v1", "v2", "v3"};
            String testFolderStr = "src/test/resources/rust_examples/application/";
            commit(folders, testFolderStr, service);

            service.checkout("create.1,get.1,getAll.1,change.1,base.1");
            Path actual = Paths.get("src/test/resources/rust_examples/application/result/main.rs");
            Path testOutput = Paths.get("src/test/resources/rust_examples/test_output/main.rs");
            assertFilesEqual(actual, testOutput);

            service.checkout("create.1,get.1,getAll.1,base.1");
            actual = Paths.get("src/test/resources/rust_examples/application/result1/main.rs");
            assertFilesEqual(actual, testOutput);
        } catch (Exception e) {
            System.out.println("Exception during checkout: " + e.getMessage());
        }
    }

    private void commit(String[] folders, String testFolderStr, EccoService service) {
        Path testFolder = Paths.get(testFolderStr);
        for (String folder : folders) {
            commitSingleDir(testFolder.resolve(folder), service);
        }
    }

}
