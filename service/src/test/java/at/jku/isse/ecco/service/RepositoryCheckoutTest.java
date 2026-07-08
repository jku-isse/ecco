package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Checkout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the commit/checkout round trip through {@link EccoService} and the
 * {@link at.jku.isse.ecco.repository.Repository} it drives - the core promise of the system being
 * that checking out a configuration reproduces exactly the file content that was committed under it.
 * Self-contained (no external fixtures) and deliberately small, unlike the fixture-driven
 * integration tests under src/integrationTest.
 */
public class RepositoryCheckoutTest {

	/**
	 * Likely a real gap, not intended behavior: committing with an empty configuration ("no
	 * features") does create a single association with an always-true condition (verified via
	 * commit.getAssociations()), but checking out that same empty configuration selects zero
	 * associations and writes no file at all - the association is apparently never considered a
	 * match for the empty configuration it was committed under. Pinned down as-is rather than
	 * silently dropped, since it directly explains the checkout NPE seen from the CLI
	 * (`ecco checkout -c ""` on a repo with only such a commit) and should be looked at separately.
	 */
	@Test
	@Timeout(30)
	public void checkout_afterUnconditionalCommit_selectsNoAssociations() throws IOException {
		Path base = Files.createTempDirectory("ecco-checkout-repro");
		EccoService service = new EccoService();
		service.setRepositoryDir(base.resolve(".ecco"));
		service.init();

		Path commitDir = base.resolve("commit");
		Files.createDirectories(commitDir);
		Files.writeString(commitDir.resolve("file.txt"), "unconditional content\n", StandardCharsets.UTF_8);
		service.setBaseDir(commitDir);
		var commit = service.commit("initial commit", "");
		assertEquals(1, commit.getAssociations().size());

		Path checkoutDir = base.resolve("checkout");
		Files.createDirectories(checkoutDir);
		service.setBaseDir(checkoutDir);
		Checkout checkout = service.checkout("");

		assertTrue(checkout.getSelectedAssociations().isEmpty());
		assertFalse(Files.exists(checkoutDir.resolve("file.txt")));

		service.close();
	}

	@Test
	@Timeout(30)
	public void checkout_withFeatureRevision_selectsMatchingVariantOnly() throws IOException {
		Path base = Files.createTempDirectory("ecco-checkout-variants");
		EccoService service = new EccoService();
		service.setRepositoryDir(base.resolve(".ecco"));
		service.init();

		Path variantADir = base.resolve("variantA");
		Files.createDirectories(variantADir);
		String contentA = "variant A content\n";
		Files.writeString(variantADir.resolve("file.txt"), contentA, StandardCharsets.UTF_8);
		service.setBaseDir(variantADir);
		service.commit("commit A", "Feature.A");

		Path variantBDir = base.resolve("variantB");
		Files.createDirectories(variantBDir);
		String contentB = "variant B content\n";
		Files.writeString(variantBDir.resolve("file.txt"), contentB, StandardCharsets.UTF_8);
		service.setBaseDir(variantBDir);
		service.commit("commit B", "Feature.B");

		Path checkoutDir = base.resolve("checkout");
		Files.createDirectories(checkoutDir);
		service.setBaseDir(checkoutDir);
		service.checkout("Feature.A");
		assertEquals(contentA, Files.readString(checkoutDir.resolve("file.txt"), StandardCharsets.UTF_8));

		DeleteRecursively.deleteContents(checkoutDir);
		service.checkout("Feature.B");
		assertEquals(contentB, Files.readString(checkoutDir.resolve("file.txt"), StandardCharsets.UTF_8));

		service.close();
	}

	private static final class DeleteRecursively {
		static void deleteContents(Path dir) throws IOException {
			try (var stream = Files.list(dir)) {
				for (Path entry : stream.toList()) {
					if (Files.isDirectory(entry)) {
						deleteContents(entry);
					}
					Files.delete(entry);
				}
			}
		}
	}
}
