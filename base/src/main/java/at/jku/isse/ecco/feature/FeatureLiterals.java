package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The logic variables features and feature revisions are named by in condition strings (LogicNG
 * cannot parse "." and "-", so both become "_"): a revision is "name_id", as retroactive conditions
 * name it, and a feature is "name", as proactive conditions (e.g. VEVOS presence conditions) name
 * it. The one place this naming is defined - it used to be repeated in four.
 * <p>
 * The naming is not injective ("shopping-cart" and "shopping_cart" are one variable, as are the
 * revisions "A_B.1" and "A.B_1"), and conditions that share a variable checked out wrong content
 * without a warning: {@link #checkDistinct} refuses such names where they enter a repository.
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

	/**
	 * Refuses the features and revisions of [added] that are not yet in a repository with [features]
	 * if one would get the variable of another - in the repository or among [added]: conditions could
	 * not tell them apart. Names that already share a variable in a repository are left as they are.
	 */
	public static void checkDistinct(Collection<? extends Feature> features, Collection<? extends FeatureRevision> added) {
		Map<String, Owner> owners = new HashMap<>();
		Set<String> known = new HashSet<>();
		for (Feature feature : features) {
			Owner owner = Owner.of(feature);
			owners.putIfAbsent(featureVariable(feature), owner);
			known.add(owner.key);
			for (FeatureRevision revision : feature.getRevisions()) {
				Owner revisionOwner = Owner.of(revision);
				owners.putIfAbsent(revisionVariable(revision), revisionOwner);
				known.add(revisionOwner.key);
			}
		}
		Set<String> clashes = new LinkedHashSet<>();
		for (FeatureRevision revision : added) {
			List<Map.Entry<String, Owner>> candidates = new ArrayList<>(2);
			candidates.add(Map.entry(featureVariable(revision.getFeature()), Owner.of(revision.getFeature())));
			candidates.add(Map.entry(revisionVariable(revision), Owner.of(revision)));
			for (Map.Entry<String, Owner> candidate : candidates) {
				Owner owner = candidate.getValue();
				if (known.contains(owner.key))
					continue;
				Owner other = owners.putIfAbsent(candidate.getKey(), owner);
				if (other != null && !other.key.equals(owner.key))
					clashes.add(owner.text + " and " + other.text);
			}
		}
		if (!clashes.isEmpty())
			throw new EccoException("Conditions name features with \".\" and \"-\" read as \"_\", so they could not tell "
					+ String.join(", ", clashes) + " apart. Rename one of each.");
	}

	/** A feature or revision, by identity (key) and as shown (text). */
	private record Owner(String key, String text) {
		static Owner of(Feature feature) {
			return new Owner("f " + feature.getId(), "the feature \"" + feature.getName() + "\"");
		}

		static Owner of(FeatureRevision revision) {
			return new Owner("r " + revision.getFeature().getId() + " " + revision.getId(),
					"the revision \"" + revision.getFeature().getName() + "." + revision.getId() + "\"");
		}
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
