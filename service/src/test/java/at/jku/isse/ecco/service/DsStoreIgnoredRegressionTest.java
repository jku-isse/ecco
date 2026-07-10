package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a user-reported annoyance: on macOS, Finder litters browsed folders with
 * ".DS_Store" metadata files, which the FilePlugin's catch-all "**" reader would otherwise pick
 * up and commit as ordinary file content, then recreate on every checkout.
 * <p>
 * Fixed in {@code DispatchReader.init()} - a freshly created ".ignores" file is now seeded with
 * ".DS_Store" and "**\/.DS_Store" (root-level and nested) instead of being left empty.
 */
public class DsStoreIgnoredRegressionTest {

	@Test
	@Timeout(30)
	public void dsStoreIsIgnoredOnCommitAndCheckout() throws IOException {
		Path workDir = Files.createTempDirectory("dsstore-ignore-test");
		Path repoDir = workDir.resolve(".ecco");
		Path baseDir = workDir.resolve("base");
		Files.createDirectories(baseDir);

		Files.writeString(baseDir.resolve("real.txt"), "hello world");
		Files.writeString(baseDir.resolve(".DS_Store"), "not real content");
		Path subDir = baseDir.resolve("subdir");
		Files.createDirectories(subDir);
		Files.writeString(subDir.resolve(".DS_Store"), "not real content either");

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
		assertFalse(Files.exists(checkoutDir.resolve(".DS_Store")), "root .DS_Store should not have been committed/checked out");
		assertFalse(Files.exists(checkoutDir.resolve("subdir").resolve(".DS_Store")), "nested .DS_Store should not have been committed/checked out");
	}
}
