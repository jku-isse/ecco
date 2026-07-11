package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.feature.SerFeatureRevision;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import org.junit.jupiter.api.Test;
import org.logicng.formulas.Formula;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SurplusModuleSuppressorTest {

    @Test
    public void emptySurplusMap_isANoOp() {
        Checkout checkout = new Checkout();
        // no entries added -- suppressEntailed must return without building a solver at all;
        // observable effect is simply that it doesn't throw and leaves the (still-empty) map alone
        SurplusModuleSuppressor.suppressEntailed(checkout, Set.of(), FormulaFactoryProvider.getFormulaFactory().verum());
        assertTrue(checkout.getSurplusModules().isEmpty());
    }

    @Test
    public void entailedSurplusEntry_isRemoved() {
        SerFeature core = new SerFeature("f1", "Core");
        SerFeatureRevision coreR1 = core.addRevision("1");
        SerFeature someFeature = new SerFeature("f2", "SomeFeature");

        // desired: Core@1
        SerModule desiredModule = new SerModule(new Feature[]{core}, new Feature[]{});
        SerModuleRevision desiredRevision = desiredModule.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{});

        // surplus candidate: requires Core@1 AND excludes SomeFeature -- the exclusion is the part
        // that should turn out to be non-actionable, given EXCLUDES(Core,SomeFeature) + desired Core@1
        SerModule surplusModule = new SerModule(new Feature[]{core}, new Feature[]{someFeature});
        SerModuleRevision surplusRevision = surplusModule.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{someFeature});

        Checkout checkout = new Checkout();
        checkout.getSurplusModules().put(surplusRevision, "assoc-1");

        ConstraintMiner.Suggestion excludes = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.EXCLUDES, "Core", "SomeFeature", 0.5, 1.0, 4, List.of());
        Formula revisionAwareFeatureModel = FeatureModelFormula.compileRevisionAware(List.of(excludes), List.of(core, someFeature));

        SurplusModuleSuppressor.suppressEntailed(checkout, Set.of(desiredRevision), revisionAwareFeatureModel);

        assertTrue(checkout.getSurplusModules().isEmpty(),
                "EXCLUDES(Core,SomeFeature) + desired Core@1 should prove SomeFeature's absence, making the surplus entry non-actionable");
    }

    @Test
    public void unprovableSurplusEntry_isRetained() {
        SerFeature core = new SerFeature("f1", "Core");
        SerFeatureRevision coreR1 = core.addRevision("1");
        SerFeature unrelated = new SerFeature("f2", "Unrelated");
        SerFeatureRevision unrelatedR1 = unrelated.addRevision("1");

        SerModule desiredModule = new SerModule(new Feature[]{core}, new Feature[]{});
        SerModuleRevision desiredRevision = desiredModule.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{});

        // surplus candidate positively requires Unrelated@1 -- nothing in the feature model or
        // desired set says anything about it, so it must NOT be provable either way
        SerModule surplusModule = new SerModule(new Feature[]{unrelated}, new Feature[]{});
        SerModuleRevision surplusRevision = surplusModule.addRevision(new FeatureRevision[]{unrelatedR1}, new Feature[]{});

        Checkout checkout = new Checkout();
        checkout.getSurplusModules().put(surplusRevision, "assoc-1");

        Formula revisionAwareFeatureModel = FeatureModelFormula.compileRevisionAware(List.of(), List.of(core, unrelated));

        SurplusModuleSuppressor.suppressEntailed(checkout, Set.of(desiredRevision), revisionAwareFeatureModel);

        assertEquals(1, checkout.getSurplusModules().size(),
                "an entry with no proof of entailment must be conservatively retained, not removed");
        assertTrue(checkout.getSurplusModules().containsKey(surplusRevision));
    }

    @Test
    public void wrongRevisionOfMultiRevisionFeature_isNeverSuppressed() {
        SerFeature core = new SerFeature("f1", "Core");
        SerFeatureRevision coreR1 = core.addRevision("1");
        SerFeatureRevision coreR2 = core.addRevision("2");

        // desired selects revision 1
        SerModule desiredModule = new SerModule(new Feature[]{core}, new Feature[]{});
        SerModuleRevision desiredRevision = desiredModule.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{});

        // surplus candidate requires the OTHER revision (2) -- a genuine revision mismatch
        SerModule surplusModule = new SerModule(new Feature[]{core}, new Feature[]{});
        SerModuleRevision surplusRevision = surplusModule.addRevision(new FeatureRevision[]{coreR2}, new Feature[]{});

        Checkout checkout = new Checkout();
        checkout.getSurplusModules().put(surplusRevision, "assoc-1");

        Formula revisionAwareFeatureModel = FeatureModelFormula.compileRevisionAware(List.of(), List.of(core));

        SurplusModuleSuppressor.suppressEntailed(checkout, Set.of(desiredRevision), revisionAwareFeatureModel);

        assertEquals(1, checkout.getSurplusModules().size(),
                "Core@2 is not provably entailed given desired Core@1 (the at-most-one-revision-per-feature clause in fact refutes it) -- must never be suppressed");
    }

    @Test
    public void missingModulesAreNeverTouched() {
        SerFeature core = new SerFeature("f1", "Core");
        SerFeatureRevision coreR1 = core.addRevision("1");

        SerModule missingModule = new SerModule(new Feature[]{core}, new Feature[]{});
        SerModuleRevision missingRevision = missingModule.addRevision(new FeatureRevision[]{coreR1}, new Feature[]{});

        Checkout checkout = new Checkout();
        checkout.getMissing().add(missingRevision);
        // no surplus entries at all -- suppressEntailed should be a no-op and definitely never
        // touch getMissing()

        SurplusModuleSuppressor.suppressEntailed(checkout, Set.of(), FormulaFactoryProvider.getFormulaFactory().verum());

        assertEquals(1, checkout.getMissing().size());
        assertTrue(checkout.getMissing().contains(missingRevision));
        assertFalse(checkout.getSurplusModules().containsKey(missingRevision));
    }
}
