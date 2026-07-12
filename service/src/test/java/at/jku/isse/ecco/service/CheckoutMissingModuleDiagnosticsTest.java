package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.module.ModuleRevisions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real end-to-end proof that {@link at.jku.isse.ecco.repository.Repository.Op#compose} (via
 * {@link ModuleRevisions}) trims, ranks, and renders MISSING checkout diagnostics in a
 * user-understandable form, with a location anchor -- rather than a raw, unordered dump of
 * {@link ModuleRevision#toString()} with no context.
 */
public class CheckoutMissingModuleDiagnosticsTest {

	@Test
	@Timeout(30)
	public void checkout_requestingTwoNeverCoCommittedFeatures_reportsOneUserUnderstandableLocatedMissingItem() throws IOException {
		Path base = Files.createTempDirectory("ecco-missing-diagnostics");
		EccoService service = new EccoService();
		service.setRepositoryDir(base.resolve(".ecco"));
		service.init();

		// FeatureA and FeatureB are each committed alone -- they exist individually in the
		// repository but were never combined in any single commit.
		Path dirA = base.resolve("a");
		Files.createDirectories(dirA);
		Files.writeString(dirA.resolve("file.txt"), "a\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirA);
		Commit commitA = service.commit("commit A", "FeatureA");
		String associationIdA = commitA.getAssociations().iterator().next().getId();

		Path dirB = base.resolve("b");
		Files.createDirectories(dirB);
		Files.writeString(dirB.resolve("file.txt"), "b\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirB);
		Commit commitB = service.commit("commit B", "FeatureB");
		String associationIdB = commitB.getAssociations().iterator().next().getId();

		Path checkoutDir = base.resolve("checkout");
		Files.createDirectories(checkoutDir);
		service.setBaseDir(checkoutDir);
		Checkout checkout = service.checkout("FeatureA,FeatureB");

		// exactly one trimmed missing item: the (FeatureA, FeatureB) combination itself -- neither
		// singleton is missing (each was committed on its own), so there's no lower-order missing
		// sub-combination and it isn't trimmed away.
		assertEquals(1, checkout.getMissing().size());
		ModuleRevision missingAB = checkout.getMissing().iterator().next();
		assertEquals(1, missingAB.getOrder());

		// user-understandable rendering: real feature names, not a raw hash-suffixed toString().
		assertEquals("FeatureA + FeatureB", ModuleRevisions.describe(missingAB));

		// location in artifacts: points at the two real associations each feature actually lives in.
		String location = checkout.getMissingLocations().get(missingAB);
		assertTrue(location.contains("FeatureA in association " + associationIdA), location);
		assertTrue(location.contains("FeatureB in association " + associationIdB), location);

		service.close();
	}

}
