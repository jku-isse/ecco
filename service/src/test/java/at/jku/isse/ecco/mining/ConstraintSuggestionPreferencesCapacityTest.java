package at.jku.isse.ecco.mining;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rejected suggestions were stored as ONE comma-joined Preferences value, and a Preferences value
 * may be at most Preferences.MAX_VALUE_LENGTH (8192) characters - so after a few hundred rejections
 * reject() threw IllegalArgumentException and rejecting in the GUI failed for good.
 */
public class ConstraintSuggestionPreferencesCapacityTest {

    private Path repositoryDir;

    @AfterEach
    public void forget() {
        if (repositoryDir != null) ConstraintSuggestionPreferences.forget(repositoryDir);
    }

    @Test
    public void manyRejectionsFitAndSurviveARoundTrip() throws Exception {
        repositoryDir = Files.createTempDirectory("constraint-preferences-capacity");
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            // same construction as ConstraintSuggestionPreferences.signatureOf()
            String signature = at.jku.isse.ecco.core.Constraint.buildId(ConstraintMiner.Kind.REQUIRES.name(), "featureWithALongName" + i, "anotherFeature" + i);
            ConstraintSuggestionPreferences.reject(repositoryDir, signature);
            expected.add(signature);
        }
        assertEquals(expected, ConstraintSuggestionPreferences.getRejected(repositoryDir));

        String removed = expected.iterator().next();
        ConstraintSuggestionPreferences.clearDecision(repositoryDir, removed);
        expected.remove(removed);
        assertEquals(expected, ConstraintSuggestionPreferences.getRejected(repositoryDir));
    }
}
