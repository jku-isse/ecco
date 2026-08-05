package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.web.domain.model.CloseOperationResponse;
import at.jku.isse.ecco.web.domain.model.OpenOperationResponse;
import at.jku.isse.ecco.web.domain.model.OperationResponse;
import at.jku.isse.ecco.web.domain.model.ReducedArtifactPlugin;
import at.jku.isse.ecco.web.rest.EccoApplication;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OperationRepository drives the open/close/create state machine over a real EccoApplication/
 * EccoService, plus the ZIP upload handling. Tested against real temp-dir repositories, matching the
 * pattern used for the other web/domain/repository tests in this suite.
 */
@Timeout(30)
public class OperationRepositoryTest {

    private EccoApplication application;

    @AfterEach
    public void tearDown() {
        if (application != null) {
            application.close();
        }
    }

    @Test
    public void createInitializesANewRepositoryAndListsRealArtifactPlugins() throws IOException {
        Path repoRoot = Files.createTempDirectory("operation-repository-create");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);

        OperationResponse response = operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CREATE");

        assertInstanceOf(OpenOperationResponse.class, response);
        assertTrue(response.isEccoServiceIsInitialized());
        OpenOperationResponse openResponse = (OpenOperationResponse) response;
        assertTrue(openResponse.getArtifactPlugins().length > 0, "at least the file/text/image adapters registered as test dependencies should show up");
        for (ReducedArtifactPlugin plugin : openResponse.getArtifactPlugins()) {
            assertNotNull(plugin.getPluginID());
        }
    }

    /**
     * Real bug found by reading the code, confirmed here rather than assumed: the constructor
     * correctly assigns name/description onto ReducedArtifactPlugin's fields, but getName()/
     * getDescription() unconditionally {@code return null;} instead of returning those fields - any
     * real plugin's display name/description is silently lost by the time it reaches this response
     * model. Not fixed here since it's a test-only pass.
     */
    @Test
    public void reducedArtifactPluginGetNameAndGetDescriptionAlwaysReturnNullRegardlessOfWhatWasSet() throws IOException {
        Path repoRoot = Files.createTempDirectory("operation-repository-plugin-getter-bug");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);

        OpenOperationResponse response = (OpenOperationResponse)
                operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CREATE");

        assertTrue(response.getArtifactPlugins().length > 0, "sanity check: there is at least one plugin to observe the bug on");
        for (ReducedArtifactPlugin plugin : response.getArtifactPlugins()) {
            assertNull(plugin.getName(), "getName() is dead code that always returns null");
            assertNull(plugin.getDescription(), "getDescription() is dead code that always returns null");
        }
    }

    @Test
    public void openOnANonexistentRepositoryThrowsNotFoundException() throws IOException {
        Path repoRoot = Files.createTempDirectory("operation-repository-open-missing");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);

        assertThrows(NotFoundException.class,
                () -> operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "OPEN"));
    }

    @Test
    public void openOnAPreviouslyCreatedRepositoryReopensIt() throws IOException {
        Path repoRoot = Files.createTempDirectory("operation-repository-open-existing");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);
        operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CREATE");
        operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CLOSE");

        OperationResponse response = operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "OPEN");

        assertInstanceOf(OpenOperationResponse.class, response);
        assertTrue(response.isEccoServiceIsInitialized());
    }

    @Test
    public void closeMarksTheServiceAsNoLongerInitialized() throws IOException {
        Path repoRoot = Files.createTempDirectory("operation-repository-close");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);
        operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CREATE");

        OperationResponse response = operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CLOSE");

        assertInstanceOf(CloseOperationResponse.class, response);
        assertFalse(response.isEccoServiceIsInitialized());
    }

    @Test
    public void anUnrecognizedOperationThrowsNotAllowedException() throws IOException {
        Path repoRoot = Files.createTempDirectory("operation-repository-unknown-op");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);

        assertThrows(NotAllowedException.class,
                () -> operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "DELETE"));
    }

    @Test
    public void saveZIPFileOnPathWritesTheUploadedStreamUnderTheRepositorysBaseDirectory() throws Exception {
        Path repoRoot = Files.createTempDirectory("operation-repository-zip-save");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);
        operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CREATE");

        byte[] content = "zip content".getBytes(StandardCharsets.UTF_8);
        FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName("upload.zip").build();

        String savedDirectory = operationRepository.saveZIPFileOnPath(new ByteArrayInputStream(content), fileDetail);

        Path savedFile = Path.of(savedDirectory, "upload.zip");
        assertTrue(Files.exists(savedFile));
        assertArrayEquals(content, Files.readAllBytes(savedFile));
        assertEquals(repoRoot.resolve("UNIQUE_DIRECTORY_TO_SAVE_ZIPFILE").toString(), savedDirectory);
    }

    /**
     * Real gap found by reading the code, confirmed here rather than assumed: despite its javadoc
     * ("das Committen der Dateien ... funktioniert nicht" - "committing the files does not work"),
     * commitFilesInsideSavedRepositoryOnPath() only logs; it never actually unzips or commits
     * anything. Characterizing that as-is - a real functional gap, not something to guess a fix for
     * in a test-only pass.
     */
    @Test
    public void commitFilesInsideSavedRepositoryOnPathIsANoOpThatNeverCreatesACommit() throws IOException {
        Path repoRoot = Files.createTempDirectory("operation-repository-commit-noop");
        application = new EccoApplication();
        OperationRepository operationRepository = new OperationRepository(application);
        operationRepository.doOpenCloseCreateOperationOnRepository(repoRoot.toString(), "CREATE");

        int commitsBefore = application.getEccoService().getCommits().size();

        assertDoesNotThrow(() -> operationRepository.commitFilesInsideSavedRepositoryOnPath("/some/path", "upload.zip"));

        assertEquals(commitsBefore, application.getEccoService().getCommits().size(), "no commit is ever actually created");
    }
}
