package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;

/**
 * The logic variables features and feature revisions are named by in condition strings (LogicNG
 * cannot parse "." and "-", so both become "_"): a revision is "name_id", as retroactive conditions
 * name it, and a feature is "name", as proactive conditions (e.g. VEVOS presence conditions) name
 * it. The one place this naming is defined - it used to be repeated in four.
 */
public final class FeatureLiterals {

	private FeatureLiterals() {
	}

	/** [text] with "." and "-" as "_" - a name, an id, or a whole condition string. */
	public static String sanitize(String text) {
		// fast path: condition strings get very large (hundreds of KB) and most are clean
		if (text.indexOf('.') < 0 && text.indexOf('-') < 0)
			return text;
		return text.replace('.', '_').replace('-', '_');
	}

	/** The variable of a feature, whichever revision: its sanitized name. */
	public static String featureVariable(Feature feature) {
		return sanitize(feature.getName());
	}

	/** The variable of one revision of a feature: "name_id", sanitized. */
	public static String revisionVariable(FeatureRevision revision) {
		return sanitize(revision.getFeature().getName()) + "_" + sanitize(revision.getId());
	}

	/** Every selected revision and its feature true - what conditions are evaluated with. */
	public static Assignment assignment(Configuration configuration) {
		FormulaFactory factory = FormulaFactoryProvider.getFormulaFactory();
		Assignment assignment = new Assignment();
		for (FeatureRevision revision : configuration.getFeatureRevisions()) {
			assignment.addLiteral(factory.literal(revisionVariable(revision), true));
			assignment.addLiteral(factory.literal(featureVariable(revision.getFeature()), true));
		}
		return assignment;
	}
}
