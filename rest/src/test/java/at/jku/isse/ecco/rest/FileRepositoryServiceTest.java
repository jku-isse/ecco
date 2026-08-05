package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.models.RestRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FileRepositoryService.repoStorage used to be a hardcoded static field resolving to a real,
 * persistent path under this repo's own working tree (see Settings.STORAGE_LOCATION_OF_REPOSITORIES,
 * and examples/newTestRepro/ - a leftover from RestTest.createNewRepo(), which exercises exactly that
 * path). It's now constructor-injectable (see FileRepositoryService(Path)), so these point it at a
 * temp directory instead.
 */
public class FileRepositoryServiceTest {

    private CompletedFileUpload mockUpload(String filename, String content) throws IOException {
        CompletedFileUpload upload = mock(CompletedFileUpload.class);
        when(upload.getFilename()).thenReturn(filename);
        when(upload.getBytes()).thenReturn(content.getBytes());
        return upload;
    }

    @Test
    @Timeout(30)
    public void createRepositoryCreatesADirectoryAndInitializesIt() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-create");
        FileRepositoryService service = new FileRepositoryService(storage);

        RepositoryHandler handler = service.createRepository("my-repo");

        assertTrue(Files.isDirectory(storage.resolve("my-repo")));
        assertTrue(handler.isInitialized());
        assertEquals("my-repo", handler.getName());
    }

    @Test
    @Timeout(30)
    public void createRepositoryWithADuplicateNameThrows() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-create-dup");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("my-repo");

        HttpStatusException exception = assertThrows(HttpStatusException.class, () -> service.createRepository("my-repo"));
        assertEquals(HttpStatus.IM_USED, exception.getStatus());
    }

    @Test
    @Timeout(30)
    public void getRepositoryOfUnknownIdThrowsNotFound() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-get-unknown");
        FileRepositoryService service = new FileRepositoryService(storage);

        HttpStatusException exception = assertThrows(HttpStatusException.class, () -> service.getRepository(1));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    @Timeout(30)
    public void getRepositoryOfKnownIdDelegatesToItsHandler() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-get-known");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("my-repo");

        RestRepository repository = service.getRepository(1);

        assertEquals("my-repo", repository.getName());
    }

    @Test
    @Timeout(30)
    public void getRepositoriesOnFreshStorageIsEmpty() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-empty");
        FileRepositoryService service = new FileRepositoryService(storage);

        assertTrue(service.getRepositories().isEmpty());
    }

    @Test
    @Timeout(30)
    public void getRepositoriesDiscoversRepositoriesCreatedByAnotherServiceInstance() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-discover");
        new FileRepositoryService(storage).createRepository("existing-repo");

        FileRepositoryService freshService = new FileRepositoryService(storage);
        Map<Integer, RepositoryHandler> discovered = freshService.getRepositories();

        assertEquals(1, discovered.size());
        assertEquals("existing-repo", discovered.values().iterator().next().getName());
    }

    @Test
    @Timeout(30)
    public void deleteRepositoryRemovesItsDirectoryAndTracking() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-delete");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("my-repo");

        service.deleteRepository(1);

        assertFalse(Files.exists(storage.resolve("my-repo")));
        assertTrue(service.getRepositories().isEmpty());
    }

    @Test
    @Timeout(30)
    public void cloneRepositoryCopiesAllFilesToTheNewDirectory() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-clone");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("original");

        service.cloneRepository(1, "clone");

        assertTrue(Files.isDirectory(storage.resolve("clone")));
        assertTrue(Files.isDirectory(storage.resolve("clone").resolve(".ecco")), "the .ecco directory created by init() should have been copied too");
    }

    @Test
    @Timeout(30)
    public void cloneRepositoryWithADuplicateNameThrows() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-clone-dup");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("original");
        service.createRepository("clone");

        HttpStatusException exception = assertThrows(HttpStatusException.class, () -> service.cloneRepository(1, "clone"));
        assertEquals(HttpStatus.IM_USED, exception.getStatus());
    }

    @Test
    @Timeout(30)
    public void addCommitWritesUploadedFilesAndCommits() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-add-commit");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("my-repo");

        CompletedFileUpload upload = mockUpload("\\file.txt", "hello\n");
        RestRepository result = service.addCommit(1, "first commit", "Core", "alice", List.of(upload));

        assertEquals(1, result.getCommits().size());
        assertEquals("first commit", result.getCommits().iterator().next().getCommitMessage());
    }

    @Test
    @Timeout(30)
    public void addCommitOfUnknownRepositoryThrows() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-add-commit-unknown");
        FileRepositoryService service = new FileRepositoryService(storage);

        assertThrows(NullPointerException.class, () -> service.addCommit(1, "msg", "Core", "alice", List.of()));
    }

    @Test
    @Timeout(30)
    public void checkoutProducesAZipContainingTheCheckedOutFiles() throws Exception {
        Path storage = Files.createTempDirectory("file-repository-service-checkout");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("my-repo");
        CompletedFileUpload upload = mockUpload("\\file.txt", "hello\n");
        RestRepository afterCommit = service.addCommit(1, "first commit", "Core", "alice", List.of(upload));
        String variantId = afterCommit.getVariants().iterator().next().getId();

        Path zip = service.checkout(1, variantId);

        assertTrue(Files.exists(zip));
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertNotNull(zipFile.getEntry("file.txt"));
        }
    }

    @Test
    @Timeout(30)
    public void addVariantDelegatesToTheRepositoryHandler() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-add-variant");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("my-repo");
        CompletedFileUpload upload = mockUpload("\\file.txt", "hello\n");
        service.addCommit(1, "first commit", "Core", "alice", List.of(upload));

        RestRepository result = service.addVariant(1, "second", "", "desc");

        assertEquals(2, result.getVariants().size());
    }

    /**
     * Regression test for a real bug, now fixed: Repository.Op.merge()
     * (base/src/main/java/at/jku/isse/ecco/repository/Repository.java, the "for every association in
     * other repository" loop) builds a {@code Commit} per merged association via
     * {@code this.extract(association, commit)}, but used to never call
     * {@code this.addCommit(commit)} on it afterward - so those commits were silently discarded and
     * never appeared in the merged-into repository's commit log, even though the underlying
     * features/associations/data did transfer correctly. Fixed by adding the missing addCommit(commit)
     * call - see RepositoryOpExtractTest#mergeRegistersAMergeCommitPerMergedAssociation for the same
     * fix pinned at the algorithm level.
     */
    @Test
    @Timeout(30)
    public void forkRepositoryRegistersAMergeCommitPerMergedAssociation() throws IOException {
        Path storage = Files.createTempDirectory("file-repository-service-fork");
        FileRepositoryService service = new FileRepositoryService(storage);
        service.createRepository("original");
        CompletedFileUpload upload = mockUpload("\\file.txt", "hello\n");
        service.addCommit(1, "first commit", "Core", "alice", List.of(upload));

        service.forkRepository(1, "forked", "");

        RestRepository forked = service.getRepository(2);
        assertTrue(forked.getFeatures().stream().anyMatch(f -> "Core".equals(f.getName())), "the original's feature data should have been merged in");
        assertEquals(1, forked.getCommits().size(), "forking one association should register one merge commit");
    }
}
