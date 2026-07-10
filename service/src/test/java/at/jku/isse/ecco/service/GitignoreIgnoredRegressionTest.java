package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a user request: a ".gitignore" file (relevant to the "Import from Git"
 * feature, which extracts a git clone's full working tree per commit) should never be committed
 * as ordinary file content by the FilePlugin's catch-all "**" reader.
 * <p>
 * Fixed in {@code DispatchReader.init()} - a freshly created ".ignores" file is now seeded with
 * ".gitignore" and "**\/.gitignore" (root-level and nested), alongside the existing ".DS_Store"
 * patterns.
 */
public class GitignoreIgnoredRegressionTest {

	@Test
	@Timeout(30)
	public void gitignoreIsIgnoredOnCommitAndCheckout() throws IOException {
		Path workDir = Files.createTempDirectory("gitignore-ignore-test");
		Path repoDir = workDir.resolve(".ecco");
		Path baseDir = workDir.resolve("base");
		Files.createDirectories(baseDir);

		Files.writeString(baseDir.resolve("real.txt"), "hello world");
		Files.writeString(baseDir.resolve(".gitignore"), "*.log\nbuild/\n");
		Path subDir = baseDir.resolve("subdir");
		Files.createDirectories(subDir);
		Files.writeString(subDir.resolve(".gitignore"), "*.tmp\n");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();
			service.setBaseDir(baseDir);
			service.commit("initial commit", "Feature.A");
		}

		Path checkoutDir = workDir.resolve("checkout");
		Files.createDirectories(checkoutDir);
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.open();
			service.setBaseDir(checkoutDir);
			service.checkout("Feature.A");
		}

		assertTrue(Files.exists(checkoutDir.resolve("real.txt")), "real committed file should be checked out");
		assertFalse(Files.exists(checkoutDir.resolve(".gitignore")), "root .gitignore should not have been committed/checked out");
		assertFalse(Files.exists(checkoutDir.resolve("subdir").resolve(".gitignore")), "nested .gitignore should not have been committed/checked out");
	}
}