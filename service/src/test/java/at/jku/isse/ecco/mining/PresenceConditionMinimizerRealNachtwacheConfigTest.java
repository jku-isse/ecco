package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.logicng.formulas.Formula;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same claim as {@link PresenceConditionMinimizerRealRepoTest} (minimized conditions agree with the
 * real {@code Condition.holds()} on every actually-committed configuration), but against a larger,
 * more realistic feature structure: 8 real configuration strings from an actual repository
 * ("nachtwache", a lilypond piece the maintainer works on -- SATB choral voices, each with
 * notes/dynamics/markup/lyrics sub-features), rather than a 3-association synthetic toy repo.
 *
 * <p>Content is synthetic (one small file per feature, not the real .ly source, which isn't checked
 * into this repository -- and can't be, since it's outside the repo entirely) but the 27 feature
 * names and the 8 configurations' subset/overlap structure are real, supplied directly by the
 * maintainer from their own repository rather than invented for this test.
 */
public class PresenceConditionMinimizerRealNachtwacheConfigTest {

    // Supplied verbatim by the maintainer from a real "nachtwache" repository; C1 (index 0) is the
    // fullest configuration, C8 (index 7) the smallest. C1..C6+C8 nest strictly (each a proper
    // subset of the next larger one). C7 (index 6) is a genuine sibling branch: a subset of C3
    // (index 2), but not comparable to C4/C5/C6/C8 -- it keeps the soprano dynamics/markup features
    // they drop, and drops the alto/tenor/bass detail they keep.
    private static final List<String> CONFIGURATION_STRINGS = List.of(
            "atwonotes.1, aonemarkup.1, tonedyn.1, ttwodyn.1, slyr.1, sdyn.1, bmarkup.1, tonenotes.1, ttwomarkup.1, global.1, bnotes.1, smarkup.1, atwolyr.1, bslyr.1, ttwolyr.1, meta.1, header.1, tonemarkup.1, atwodyn.1, aonedyn.1, aonenotes.1, tonelyr.1, aonelyr.1, bdyn.1, snotes.1, atwomarkup.1, ttwonotes.1",
            "aonemarkup.1, tonedyn.1, ttwodyn.1, slyr.1, sdyn.1, bmarkup.1, tonenotes.1, ttwomarkup.1, global.1, bnotes.1, smarkup.1, bslyr.1, ttwolyr.1, meta.1, header.1, tonemarkup.1, aonedyn.1, aonenotes.1, tonelyr.1, aonelyr.1, bdyn.1, snotes.1, ttwonotes.1",
            "aonemarkup.1, tonedyn.1, slyr.1, sdyn.1, bmarkup.1, tonenotes.1, global.1, bnotes.1, smarkup.1, bslyr.1, meta.1, header.1, tonemarkup.1, aonedyn.1, aonenotes.1, tonelyr.1, aonelyr.1, bdyn.1, snotes.1",
            "aonemarkup.1,  slyr.1,  bmarkup.1, tonenotes.1, global.1, bnotes.1, smarkup.1, bslyr.1, meta.1, tonemarkup.1,  aonenotes.1, tonelyr.1, aonelyr.1,  snotes.1, header.1",
            "slyr.1, tonenotes.1, global.1, bnotes.1, bslyr.1, meta.1, aonenotes.1, tonelyr.1, aonelyr.1, snotes.1, header.1",
            "tonenotes.1, global.1, bnotes.1,  meta.1, aonenotes.1, snotes.1, header.1",
            "global.1, meta.1, snotes.1, header.1, slyr.1, smarkup.1, sdyn.1",
            "global.1, meta.1, bnotes.1, header.1"
    );

    @Test
    @Timeout(60)
    public void minimizedConditions_agreeWithRealHolds_onRealNachtwacheConfigurations() throws IOException {
        Path workDir = Files.createTempDirectory("minimizer-nachtwache-config-test");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            for (int i = 0; i < CONFIGURATION_STRINGS.size(); i++) {
                String configurationString = CONFIGURATION_STRINGS.get(i);
                Path variantDir = workDir.resolve("v" + i);
                writeFeatureFiles(variantDir, featureNamesOf(configurationString));

                service.setBaseDir(variantDir);
                service.commit("variant " + i, configurationString);
            }

            // mine, then accept every hard suggestion -- a "reviewer who trusts exception-free
            // rules" stance, same as PresenceConditionMinimizerRealRepoTest, but here there's real
            // REQUIRES structure (not just one MANDATORY feature) to actually exercise.
            List<Set<String>> configs = ConfigurationBridge.readConfigurations(service);
            List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(4, 0.9, null).mine(configs);

            List<ConstraintMiner.Suggestion> hardSuggestions = new ArrayList<>();
            for (ConstraintMiner.Suggestion suggestion : mined) {
                if (suggestion.isHard()) {
                    hardSuggestions.add(suggestion);
                    ConstraintSuggestionPreferences.accept(repoDir, ConstraintSuggestionPreferences.signatureOf(suggestion));
                }
            }
            assertFalse(hardSuggestions.isEmpty(), "sanity check: expected at least one hard suggestion from real nachtwache-derived configurations");

            Set<String> accepted = ConstraintSuggestionPreferences.getAccepted(repoDir);
            List<ConstraintMiner.Suggestion> acceptedSuggestions = new ArrayList<>();
            for (ConstraintMiner.Suggestion suggestion : mined) {
                if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
                    acceptedSuggestions.add(suggestion);
                }
            }
            assertEquals(hardSuggestions.size(), acceptedSuggestions.size(), "sanity check: everything accepted should come back as accepted");

            Formula featureModel = FeatureModelFormula.compile(acceptedSuggestions);

            List<Commit> commits = new ArrayList<>(service.getCommits());
            assertEquals(CONFIGURATION_STRINGS.size(), commits.size());

            int checkedPairs = 0;
            boolean atLeastOneSimplified = false;
            for (Association association : service.getRepository().getAssociations()) {
                Condition condition = association.computeCondition();
                List<PresenceConditionMinimizer.Term> originalTerms = ModuleConditionBridge.toTerms(condition);
                List<PresenceConditionMinimizer.Term> minimizedTerms = PresenceConditionMinimizer.minimize(featureModel, originalTerms);

                if (!PresenceConditionMinimizer.format(originalTerms).equals(PresenceConditionMinimizer.format(minimizedTerms))) {
                    atLeastOneSimplified = true;
                }

                for (Commit commit : commits) {
                    Configuration configuration = commit.getConfiguration();
                    boolean realHolds = condition.holds(configuration);
                    boolean minimizedHolds = holds(minimizedTerms, featureNamesOf(configuration));
                    assertEquals(realHolds, minimizedHolds, "association " + association.getId()
                            + " disagreed with the real Condition.holds() for configuration " + configuration);
                    checkedPairs++;
                }
            }
            assertTrue(checkedPairs > 0, "expected at least one association/configuration pair to actually be checked");
            assertTrue(atLeastOneSimplified, "expected at least one association's condition to actually be simplified given the accepted constraints");
        }
    }

    /** {@code "atwonotes.1, aonemarkup.1, ..."} -> {@code ["atwonotes", "aonemarkup", ...]}. */
    private static List<String> featureNamesOf(String configurationString) {
        List<String> names = new ArrayList<>();
        for (String token : configurationString.split(",")) {
            String trimmed = token.trim();
            int dot = trimmed.lastIndexOf('.');
            names.add(dot < 0 ? trimmed : trimmed.substring(0, dot));
        }
        return names;
    }

    private static void writeFeatureFiles(Path dir, List<String> featureNames) throws IOException {
        Files.createDirectories(dir);
        for (String featureName : featureNames) {
            // same fixed content every time a given feature reappears across variants, so ECCO's
            // structural diffing recognizes it as the same artifact rather than a new one
            Files.writeString(dir.resolve(featureName + ".txt"), featureName + "\n");
        }
    }

    private static Set<String> featureNamesOf(Configuration configuration) {
        Set<String> names = new HashSet<>();
        for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
            names.add(featureRevision.getFeature().getName());
        }
        return names;
    }

    /**
     * Mirrors {@code Module.holds()}/{@code Condition.holds()}'s real semantics; see
     * {@link PresenceConditionMinimizerRealRepoTest} for the full rationale.
     */
    private static boolean holds(List<PresenceConditionMinimizer.Term> terms, Set<String> selectedFeatureNames) {
        for (PresenceConditionMinimizer.Term term : terms) {
            if (selectedFeatureNames.containsAll(term.positive)
                    && term.negative.stream().noneMatch(selectedFeatureNames::contains)) {
                return true;
            }
        }
        return false;
    }
}