package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.module.ModuleRevisions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

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

		// the suggested fix uses the full checkout configuration (here, just A+B) with exact
		// revision ids -- not bare feature names, which would resolve to whatever the latest
		// revision happens to be at commit time, not necessarily the one actually being checked out.
		String suggestedFix = ModuleRevisions.suggestFix(missingAB, checkout.getConfiguration());
		for (var featureRevision : checkout.getConfiguration().getFeatureRevisions()) {
			assertTrue(suggestedFix.contains(featureRevision.getFeature().getName() + "." + featureRevision.getId()), suggestedFix);
		}

		// the real .warnings file written to disk carries the same suggested fix -- this is what a
		// CLI user actually sees, not just what the in-memory Checkout object holds.
		String warnings = Files.readString(checkoutDir.resolve(EccoService.WARNINGS_FILE_NAME), StandardCharsets.UTF_8);
		assertTrue(warnings.contains(suggestedFix), warnings);

		service.close();
	}

	@Test
	@Timeout(30)
	public void checkout_afterCommittingExactlyAsSuggested_noLongerReportsThatCombinationAsMissing() throws IOException {
		Path base = Files.createTempDirectory("ecco-missing-diagnostics-fixed");
		EccoService service = new EccoService();
		service.setRepositoryDir(base.resolve(".ecco"));
		service.init();

		Path dirA = base.resolve("a");
		Files.createDirectories(dirA);
		Files.writeString(dirA.resolve("file.txt"), "a\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirA);
		service.commit("commit A", "FeatureA");

		Path dirB = base.resolve("b");
		Files.createDirectories(dirB);
		Files.writeString(dirB.resolve("file.txt"), "b\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirB);
		service.commit("commit B", "FeatureB");

		Path checkoutDirBefore = base.resolve("checkout-before-fix");
		Files.createDirectories(checkoutDirBefore);
		service.setBaseDir(checkoutDirBefore);
		Checkout checkoutBeforeFix = service.checkout("FeatureA,FeatureB");

		assertEquals(1, checkoutBeforeFix.getMissing().size(),
				"sanity check: (FeatureA, FeatureB) starts out missing, same setup as the other test");

		// follow the suggested fix literally: commit new content under the exact configuration
		// string the suggestion embeds (feature name + exact revision id, for every feature in the
		// full checkout configuration) -- not a paraphrase of it.
		String suggestedConfigurationString = Arrays.stream(checkoutBeforeFix.getConfiguration().getFeatureRevisions())
				.map(featureRevision -> featureRevision.getFeature().getName() + "." + featureRevision.getId())
				.sorted()
				.collect(Collectors.joining(","));

		Path dirAB = base.resolve("ab");
		Files.createDirectories(dirAB);
		Files.writeString(dirAB.resolve("file.txt"), "a and b together\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirAB);
		service.commit("commit combining A and B, as suggested", suggestedConfigurationString);

		// a fresh checkout of the exact same configuration must no longer report the combination as
		// missing -- proving the suggestion is actually correct, not just plausible-looking text.
		Path checkoutDirAfter = base.resolve("checkout-after-fix");
		Files.createDirectories(checkoutDirAfter);
		service.setBaseDir(checkoutDirAfter);
		Checkout checkoutAfterFix = service.checkout("FeatureA,FeatureB");

		assertTrue(checkoutAfterFix.getMissing().isEmpty(), checkoutAfterFix.getMissing().toString());

		service.close();
	}

	/**
	 * The GUI's "Apply Fix" button (CheckoutDetailView) replays the exact original {@link
	 * Configuration} object -- not a re-parsed string -- via {@code service.checkout(Configuration)},
	 * back into the SAME output directory, after an intervening commit() on the same EccoService
	 * instance. This is the one piece of that flow not already covered by the string-based tests
	 * above: proves reusing a Configuration object across an intervening commit(), and re-checking
	 * out into the same (now cleared) directory, is safe and correct.
	 */
	@Test
	@Timeout(30)
	public void checkout_reusingTheSameConfigurationObjectAfterAnInterveningCommit_intoTheSameDirectory_resolvesTheMissingItem() throws IOException {
		Path base = Files.createTempDirectory("ecco-missing-diagnostics-object-replay");
		EccoService service = new EccoService();
		service.setRepositoryDir(base.resolve(".ecco"));
		service.init();

		Path dirA = base.resolve("a");
		Files.createDirectories(dirA);
		Files.writeString(dirA.resolve("file.txt"), "a\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirA);
		service.commit("commit A", "FeatureA");

		Path dirB = base.resolve("b");
		Files.createDirectories(dirB);
		Files.writeString(dirB.resolve("file.txt"), "b\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirB);
		service.commit("commit B", "FeatureB");

		Path checkoutDir = base.resolve("checkout");
		Files.createDirectories(checkoutDir);
		service.setBaseDir(checkoutDir);
		Checkout checkoutBeforeFix = service.checkout("FeatureA,FeatureB");
		Configuration configurationToReplay = checkoutBeforeFix.getConfiguration();

		assertEquals(1, checkoutBeforeFix.getMissing().size());

		String suggestedConfigurationString = Arrays.stream(configurationToReplay.getFeatureRevisions())
				.map(featureRevision -> featureRevision.getFeature().getName() + "." + featureRevision.getId())
				.sorted()
				.collect(Collectors.joining(","));

		Path dirAB = base.resolve("ab");
		Files.createDirectories(dirAB);
		Files.writeString(dirAB.resolve("file.txt"), "a and b together\n", StandardCharsets.UTF_8);
		service.setBaseDir(dirAB);
		service.commit("commit combining A and B, as suggested", suggestedConfigurationString);

		// clear the ORIGINAL checkout directory (mirrors DeleteDirectoryContentsDialog in the GUI
		// flow -- checkout(Configuration) throws if .config/.warnings already exist there) and
		// re-checkout using the SAME Configuration object obtained before the intervening commit.
		for (Path entry : Files.list(checkoutDir).toList()) {
			Files.delete(entry);
		}
		service.setBaseDir(checkoutDir);
		Checkout checkoutAfterFix = service.checkout(configurationToReplay);

		assertTrue(checkoutAfterFix.getMissing().isEmpty(), checkoutAfterFix.getMissing().toString());
		assertEquals("a and b together\n", Files.readString(checkoutDir.resolve("file.txt"), StandardCharsets.UTF_8));

		service.close();
	}

}
