package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.feature.SerFeatureRevision;
import at.jku.isse.ecco.storage.ser.module.SerCondition;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModuleConditionBridgeTest {

    @Test
    public void toRevisionTerms_producesOneTermPerModuleRevision_acrossAllModules() {
        SerFeature core = new SerFeature("f1", "Core");
        SerFeatureRevision coreR1 = core.addRevision("1");
        SerFeature extra = new SerFeature("f2", "Extra");

        SerModule moduleCoreOnly = new SerModule(new Feature[]{core}, new Feature[]{});
        SerModuleRevision coreOnlyRevision = moduleCoreOnly.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{});

        SerModule moduleCoreNotExtra = new SerModule(new Feature[]{core}, new Feature[]{extra});
        SerModuleRevision coreNotExtraRevision = moduleCoreNotExtra.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{extra});

        SerCondition condition = new SerCondition();
        condition.addModuleRevision(coreOnlyRevision);
        condition.addModuleRevision(coreNotExtraRevision);

        List<PresenceConditionMinimizer.Term> terms = ModuleConditionBridge.toRevisionTerms(condition);

        assertEquals(2, terms.size(), "one term per ModuleRevision, across both Module keys");
        boolean sawCoreOnly = false, sawCoreNotExtra = false;
        for (PresenceConditionMinimizer.Term t : terms) {
            if (t.positive.equals(Set.of(coreR1.getLogicLiteralRepresentation())) && t.negative.isEmpty()) sawCoreOnly = true;
            if (t.positive.equals(Set.of(coreR1.getLogicLiteralRepresentation())) && t.negative.equals(Set.of("Extra"))) sawCoreNotExtra = true;
        }
        assertTrue(sawCoreOnly, "expected a term for Core@1 alone");
        assertTrue(sawCoreNotExtra, "expected a term for Core@1 & !Extra");
    }

    @Test
    public void absorb_collapsesRedundantTermWithinARevision_butNeverAcrossDifferentRevisions() {
        SerFeature core = new SerFeature("f1", "Core");
        SerFeatureRevision coreR1 = core.addRevision("1");
        SerFeatureRevision coreR2 = core.addRevision("2");
        SerFeature extra = new SerFeature("f2", "Extra");

        // Module A (pos=[Core], neg=[]): two ModuleRevisions, one per revision of Core -- mirrors a
        // real association whose content has been associated with BOTH revisions of Core over its
        // history (e.g. after a "Core'" re-revisioning commit, see SurplusModuleSuppressorMultiRevisionTest)
        SerModule moduleCoreOnly = new SerModule(new Feature[]{core}, new Feature[]{});
        SerModuleRevision core1Only = moduleCoreOnly.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{});
        SerModuleRevision core2Only = moduleCoreOnly.addRevision(new FeatureRevision[]{coreR2}, new Feature[]{});

        // Module B (pos=[Core], neg=[Extra]): a REDUNDANT superset of core1Only specifically (same
        // Core@1 literal plus an extra, historically-accidental !Extra qualifier) -- the exact shape
        // from the real x8 lattice-bloat finding (see memory "surplus-noise-lattice-bloat-absorption-fix")
        SerModule moduleCoreNotExtra = new SerModule(new Feature[]{core}, new Feature[]{extra});
        SerModuleRevision core1NotExtra = moduleCoreNotExtra.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{extra});

        SerCondition condition = new SerCondition();
        condition.addModuleRevision(core1Only);
        condition.addModuleRevision(core2Only);
        condition.addModuleRevision(core1NotExtra);

        List<PresenceConditionMinimizer.Term> terms = ModuleConditionBridge.toRevisionTerms(condition);
        assertEquals(3, terms.size());

        List<PresenceConditionMinimizer.Term> absorbed = PresenceConditionMinimizer.absorb(terms);

        assertEquals(2, absorbed.size(),
                "Core@1 & !Extra must be absorbed by Core@1 alone (X+XY=X); Core@2 is a different "
                        + "literal entirely and must survive untouched, not get conflated with Core@1");

        Set<String> core1Literal = Set.of(coreR1.getLogicLiteralRepresentation());
        Set<String> core2Literal = Set.of(coreR2.getLogicLiteralRepresentation());

        boolean core1Survives = absorbed.stream().anyMatch(t -> t.positive.equals(core1Literal) && t.negative.isEmpty());
        boolean core2Survives = absorbed.stream().anyMatch(t -> t.positive.equals(core2Literal) && t.negative.isEmpty());
        boolean redundantSurvives = absorbed.stream().anyMatch(t -> t.positive.equals(core1Literal) && t.negative.equals(Set.of("Extra")));

        assertTrue(core1Survives, "the minimal Core@1 term must survive");
        assertTrue(core2Survives, "Core@2 -- a genuinely different revision -- must survive independently");
        assertTrue(!redundantSurvives, "the redundant Core@1 & !Extra term must be absorbed away");
    }
}
