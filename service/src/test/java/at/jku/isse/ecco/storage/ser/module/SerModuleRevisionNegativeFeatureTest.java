package at.jku.isse.ecco.storage.ser.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.logic.LogicUtils;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import org.junit.jupiter.api.Test;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * getConditionString()'s negative-feature handling used to only negate a feature's LATEST revision
 * (feature.getLatestRevision()), not all of its revisions. Feature[] getNeg() is feature-wide (unlike
 * FeatureRevision[] getPos(), which is revision-specific), and ModuleRevision.holds(Configuration) --
 * the OTHER implementation of the same "does this negative constraint apply" check, actually used for
 * real matching -- disqualifies the module on ANY revision of a negatively-listed feature being
 * present, not just the latest. getConditionString() diverging from that meant a configuration
 * selecting an OLDER (non-latest) revision of an "excluded" feature was wrongly treated as satisfying
 * the module's exclusion.
 */
public class SerModuleRevisionNegativeFeatureTest {

	@Test
	public void negatedFeatureExcludesEveryRevisionNotJustTheLatest() {
		SerFeature featureA = new SerFeature("idA", "FeatureA");
		FeatureRevision revA = featureA.addRevision("revA1");

		SerFeature featureB = new SerFeature("idB", "FeatureB");
		FeatureRevision revB1 = featureB.addRevision("revB1"); // older
		FeatureRevision revB2 = featureB.addRevision("revB2"); // latest

		SerModule module = new SerModule(new Feature[]{featureA}, new Feature[]{featureB});
		SerModuleRevision moduleRevision = new SerModuleRevision(module, new FeatureRevision[]{revA}, new Feature[]{featureB});

		Formula condition = LogicUtils.parseString(moduleRevision.getConditionString());
		FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

		// FeatureA selected, FeatureB present via its OLDER revision (not the latest) - the module's
		// "excludes FeatureB" requirement is violated, so the condition must evaluate to false.
		Assignment assignment = new Assignment();
		assignment.addLiteral(f.literal(revA.getLogicLiteralRepresentation(), true));
		assignment.addLiteral(f.literal(revB1.getLogicLiteralRepresentation(), true));
		assignment.addLiteral(f.literal(revB2.getLogicLiteralRepresentation(), false));

		assertFalse(condition.evaluate(assignment),
				"FeatureB is present (via its older revision) so the module's exclusion of FeatureB must not be satisfied");
	}
}
