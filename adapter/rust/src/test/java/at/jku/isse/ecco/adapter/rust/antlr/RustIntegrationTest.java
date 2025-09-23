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

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        if (!Files.exists(excepted)) {
            throw new IllegalArgumentException("File does not exist: " + excepted);
        }
        if (!Files.exists(actual)) {
            throw new IllegalArgumentException("File does not exist: " + actual);
        }
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
        if (!filesAreEqual) {
            Assertions.fail("Files are not equal:\n" + diffReport);
        }
    }

    public void commitSingleDir(Path dir, EccoService service) {
        try {
            service.setBaseDir(dir);
            String version = dir.getFileName().toString();
            service.commit(version);
        } catch (Exception e) {
            System.out.println("Exception during commit of dir " + dir + ": " + e.getMessage());
        }
        service.setBaseDir(this.testDir);
    }

    public void commitAllDirsFromPath(Path basePath, EccoService service) {
        try (Stream<Path> dirs = Files.walk(basePath).filter(Files::isDirectory)){
            dirs.forEach(dir -> {
                   try {
                        service.setBaseDir(dir);
                        String version = dir.getFileName().toString();
                        service.commit(version);
                    } catch (Exception e) {
                        System.out.println("Exception during commit of dir " + dir + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.out.println("IO exception:" + e.getMessage());
        }
        service.setBaseDir(this.testDir);
    }

    @Test
    public void comments() throws Exception {
        try {
            Path testFolder = Paths.get("src/test/resources/rust_examples/commentTest/");
            commitSingleDir(testFolder, service);
            service.checkout("comments.1");
        } catch (Exception e) {
            System.out.println("Exception during checkout: " + e.getMessage());
        }
        Path result = Paths.get("src/test/resources/rust_examples/commentTest/main.rs");
        Path testOutput = Paths.get("src/test/resources/rust_examples/test_output/main.rs");
        assertFilesEqual(result, testOutput);
    }

    @Test
    public void functionWithOuterAttribute() throws Exception {
        // set a dir for ecco to use as base dir
        try {
            Path testFolder = Paths.get("src/test/resources/rust_examples/functionTest/");
            commitAllDirsFromPath(testFolder, service);
            service.checkout("hello.1,farewell.1");
        } catch (Exception e) {
            System.out.println("Exception during checkout: " + e.getMessage());
        }
        Path result = Paths.get("src/test/resources/rust_examples/functionTest/result/main.rs");
        Path testOutput = Paths.get("src/test/resources/rust_examples/test_output/main.rs");
        assertFilesEqual(result, testOutput);
    }

}
