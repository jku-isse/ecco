package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real x8-style repos in this session had zero features with more than one revision (see
 * {@code many-small-files-overhead-quantified}/session notes), so the AMO
 * (at-most-one-revision-per-feature) soundness guarantee in
 * {@link FeatureModelFormula#compileRevisionAware} has never been exercised end to end against
 * real, commit/diff-produced data -- only in {@link SurplusModuleSuppressorTest}'s hand-built
 * fixtures. This test forces a genuine multi-revision feature via the {@code "Core'"}
 * configuration-string syntax (see {@code EccoService#parseConfigurationString}) and drives a real,
 * never-before-committed (<b>intensional</b>) checkout through the full
 * {@link EccoService#checkout} pipeline, proving a real revision mismatch is never suppressed --
 * the one claim {@link SurplusModuleSuppressorTest} cannot make, since it doesn't go through real
 * commit/diff-produced associations at all.
 *
 * <p>The complementary "an entry IS correctly suppressed" claim is deliberately NOT re-proven here:
 * {@link SurplusModuleSuppressorTest#entailedSurplusEntry_isRemoved} already covers it precisely,
 * and reproducing that exact shape (a real association whose condition carries a provably-redundant
 * *negative* literal) through ECCO's actual diff algorithm is not something this test controls --
 * unlike a revision mismatch, which the {@code "'"} syntax lets us force directly and predictably.
 */
public class SurplusModuleSuppressorMultiRevisionTest {

    @Test
    @Timeout(60)
    public void intensionalCheckout_withWrongCoreRevision_neverSuppressesTheRealMismatch() throws IOException {
        Path workDir = Files.createTempDirectory("surplus-suppressor-multirev-test");
        Path repoDir = workDir.resolve(".ecco");

        Path coreDir = workDir.resolve("core");
        Files.createDirectories(coreDir);
        Files.writeString(coreDir.resolve("core.txt"), "core v1\n");

        Path coreV2Dir = workDir.resolve("core-v2");
        Files.createDirectories(coreV2Dir);
        Files.writeString(coreV2Dir.resolve("core.txt"), "core v2\n");

        Path branchADir = workDir.resolve("branchA");
        Files.createDirectories(branchADir);
        Files.writeString(branchADir.resolve("core.txt"), "core v2\n");
        Files.writeString(branchADir.resolve("branchA.txt"), "branch A\n");

        Path coreCDir = workDir.resolve("core-c");
        Files.createDirectories(coreCDir);
        Files.writeString(coreCDir.resolve("core.txt"), "core v2\n");
        Files.writeString(coreCDir.resolve("padding.txt"), "padding\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            service.setBaseDir(coreDir);
            service.commit("core v1", "Core");

            service.setBaseDir(coreV2Dir);
            service.commit("core v2", "Core'"); // forces a brand-new FeatureRevision of Core

            service.setBaseDir(branchADir);
            service.commit("branch A", "Core, BranchA"); // uses Core's latest (v2) revision

            // pad Core's occurrence count to 4: EccoService's real suppression pipeline mines with
            // a hardcoded minWitness=4 (see EccoService#acceptedSuggestions), and EXCLUDES only
            // considers a feature "attested" once it individually occurs at least minWitness times
            // -- a plain "Core" token always reuses the latest revision, so this doesn't add a 3rd
            // revision (see EccoService#parseConfigurationString's plain-name branch)
            service.setBaseDir(coreCDir);
            service.commit("core padding", "Core");

            for (int i = 1; i <= 4; i++) {
                Path extraDir = workDir.resolve("extra-" + i);
                Files.createDirectories(extraDir);
                Files.writeString(extraDir.resolve("extra.txt"), "extra v" + i + "\n");
                service.setBaseDir(extraDir);
                service.commit("extra " + i, "Extra"); // never co-committed with Core; padded to 4 too
            }

            // mine + accept EXCLUDES(Core, Extra) -- true of every actual commit above, so it
            // should mine as hard; accepted precisely so suppression has *something* to reason
            // with, proving a revision mismatch survives even in the presence of an unrelated,
            // correctly-accepted constraint (not just when the feature model is empty)
            List<Set<String>> configs = ConfigurationBridge.readConfigurations(service);
            List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(4, 0.9, null).mine(configs);
            ConstraintMiner.Suggestion excludesCoreExtra = null;
            for (ConstraintMiner.Suggestion suggestion : mined) {
                if (suggestion.kind == ConstraintMiner.Kind.EXCLUDES
                        && ((suggestion.a.equals("Core") && "Extra".equals(suggestion.b))
                        || (suggestion.a.equals("Extra") && "Core".equals(suggestion.b)))) {
                    excludesCoreExtra = suggestion;
                }
            }
            assertNotNull(excludesCoreExtra, "sanity check: EXCLUDES(Core,Extra) should have been mined, since they never co-occur");
            assertTrue(excludesCoreExtra.isHard(), "sanity check: should be hard, no counterexample exists");
            ConstraintSuggestionPreferences.accept(repoDir, ConstraintSuggestionPreferences.signatureOf(excludesCoreExtra));

            // find Core's two revisions
            Feature coreFeature = null;
            for (Feature feature : service.getRepository().getFeatures()) {
                if (feature.getName().equals("Core")) coreFeature = feature;
            }
            assertNotNull(coreFeature);
            FeatureRevision latestCoreRevision = coreFeature.getLatestRevision();
            FeatureRevision oldCoreRevision = null;
            for (FeatureRevision revision : coreFeature.getRevisions()) {
                if (!revision.equals(latestCoreRevision)) oldCoreRevision = revision;
            }
            assertNotNull(oldCoreRevision, "sanity check: Core should have two distinct revisions");
            assertNotEquals(latestCoreRevision.getId(), oldCoreRevision.getId());

            // an INTENSIONAL configuration: pins Core to its OLD revision alongside BranchA --
            // never actually committed this way (the real "branch A" commit used Core's latest revision)
            String intensionalConfigString = "Core." + oldCoreRevision.getId() + ", BranchA";

            // baseline: real surplus, suppression off
            service.setSurplusSuppressionEnabled(false);
            Path baselineCheckoutDir = workDir.resolve("checkout-baseline");
            Files.createDirectories(baselineCheckoutDir);
            service.setBaseDir(baselineCheckoutDir);
            Checkout baseline = service.checkout(intensionalConfigString);

            assertTrue(hasCoreLatestSurplus(baseline, coreFeature, latestCoreRevision),
                    "expected the branchA association's real requirement on Core@latest to show up as surplus when Core@old is what's actually desired");

            // same configuration, suppression on
            service.setSurplusSuppressionEnabled(true);
            Path suppressedCheckoutDir = workDir.resolve("checkout-suppressed");
            Files.createDirectories(suppressedCheckoutDir);
            service.setBaseDir(suppressedCheckoutDir);
            Checkout suppressed = service.checkout(intensionalConfigString);

            assertTrue(hasCoreLatestSurplus(suppressed, coreFeature, latestCoreRevision),
                    "a genuine revision mismatch (Core@latest required but Core@old desired) must never be "
                            + "suppressed, even with an accepted EXCLUDES(Core,Extra) unrelated to it -- the "
                            + "at-most-one-revision-per-feature clause makes Core@latest unprovable (in fact "
                            + "refuted) given desired Core@old");
        }
    }

    private static boolean hasCoreLatestSurplus(Checkout checkout, Feature coreFeature, FeatureRevision latestCoreRevision) {
        for (ModuleRevision surplus : checkout.getSurplusModules().keySet()) {
            for (FeatureRevision pos : surplus.getPos()) {
                if (pos.getFeature().equals(coreFeature) && pos.equals(latestCoreRevision)) {
                    return true;
                }
            }
        }
        return false;
    }
}
