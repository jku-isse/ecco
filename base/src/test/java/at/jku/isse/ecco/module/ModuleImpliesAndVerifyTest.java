package at.jku.isse.ecco.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code m1.implies(m2)} means "whenever m2 holds, m1 holds" (see ConditionTest: "A" implies
 * "A and B"). A module is a conjunction of positive and negated features, so that is the case
 * exactly when every literal of m1 also occurs in m2 - including the negated ones. The negative
 * check used to iterate this.getPos() instead of this.getNeg(), so it could never fail: "A and not
 * B" was reported to imply "A", although A-with-B satisfies the latter but not the former.
 * <p>
 * verify() is meant to reject a feature that is both in pos and in neg, but its inner loop started
 * at i + 1 and so skipped most pairs, e.g. accepting ({A}, {A}).
 */
public class ModuleImpliesAndVerifyTest {

    private final Feature a = new SerFeature("a-id", "A");
    private final Feature b = new SerFeature("b-id", "B");
    private final Feature c = new SerFeature("c-id", "C");

    private static Module module(Feature[] pos, Feature... neg) {
        return new SerModule(pos, neg);
    }

    private static Feature[] pos(Feature... features) {
        return features;
    }

    @Test
    public void moduleImpliesRequiresTheNegativeFeaturesToo() {
        assertTrue(module(pos(a)).implies(module(pos(a, b))), "A implies A and B");
        assertTrue(module(pos(a), b).implies(module(pos(a, c), b)), "A and not B implies A and C and not B");
        assertTrue(module(pos(a)).implies(module(pos(a), b)), "A implies A and not B");

        assertFalse(module(pos(a), b).implies(module(pos(a))), "A and not B does not imply A (A with B)");
        assertFalse(module(pos(a), b).implies(module(pos(a, b))), "A and not B does not imply A and B");
        assertFalse(module(pos(a, b)).implies(module(pos(a))), "A and B does not imply A");
    }

    @Test
    public void moduleRevisionImpliesRequiresTheNegativeFeaturesToo() {
        FeatureRevision a1 = a.addRevision("a1");
        FeatureRevision b1 = b.addRevision("b1");
        SerModule moduleA = new SerModule(pos(a), new Feature[0]);
        SerModule moduleANotB = new SerModule(pos(a), new Feature[]{b});
        SerModule moduleAB = new SerModule(pos(a, b), new Feature[0]);

        ModuleRevision revisionA = new SerModuleRevision(moduleA, new FeatureRevision[]{a1}, new Feature[0]);
        ModuleRevision revisionANotB = new SerModuleRevision(moduleANotB, new FeatureRevision[]{a1}, new Feature[]{b});
        ModuleRevision revisionAB = new SerModuleRevision(moduleAB, new FeatureRevision[]{a1, b1}, new Feature[0]);

        assertTrue(revisionA.implies(revisionAB));
        assertTrue(revisionA.implies(revisionANotB));
        assertFalse(revisionANotB.implies(revisionA), "A.1 and not B does not imply A.1");
        assertFalse(revisionANotB.implies(revisionAB), "A.1 and not B does not imply A.1 and B.1");
    }

    @Test
    public void verifyRejectsAFeatureThatIsBothPositiveAndNegative() {
        assertThrows(EccoException.class, () -> new SerModule(pos(a), new Feature[]{a}));
        assertThrows(EccoException.class, () -> new SerModule(pos(a, b), new Feature[]{c, b}));

        FeatureRevision a1 = a.addRevision("a1");
        SerModule moduleA = new SerModule(pos(a), new Feature[0]);
        assertThrows(EccoException.class, () -> new SerModuleRevision(moduleA, new FeatureRevision[]{a1}, new Feature[]{a}));
    }
}
