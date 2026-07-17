package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure computation behind the Knowledge Graph tab (see {@code KnowledgeGraphView} in
 * {@code gui.view.graph}): lays out Features, Commits, Associations, and Variants in one lane per
 * entity type, plus the direct (object-reference) relationships between them, with no dependency on
 * JavaFX/GraphStream so the algorithm can be reasoned about and tested on its own - mirrors
 * {@link FeatureModelTree}'s separation of pure layout from rendering.
 * <p>
 * Deliberately scoped to <em>structural</em> relationships only - real object references already in
 * the domain model - not computed/semantic ones (e.g. whether a presence condition implies a feature
 * combination). The one exception, {@code includeComputedVariantAssociationEdges}, is opt-in and
 * off by default precisely because it's the one relationship that needs evaluating a
 * {@link at.jku.isse.ecco.module.Condition} against a {@link Configuration} rather than just
 * following a field.
 * <p>
 * Artifacts are deliberately not their own node type - {@code ArtifactGraphView} already visualizes
 * artifact trees exhaustively; duplicating that here would explode node count without adding
 * insight. Instead each Association's artifact count ({@link at.jku.isse.ecco.tree.Node#countArtifacts()})
 * rides along on its {@link Placement#artifactCount}, for the view to show as a label/size hint.
 * <p>
 * Constraints are not their own node type either: REQUIRES/EXCLUDES render as a direct edge between
 * the two Feature nodes involved (not a third node in between), and MANDATORY renders as a decoration
 * on the single Feature it applies to ({@link Placement#mandatory}) rather than a self-loop edge -
 * both are just a more direct rendering of exactly the same {@link Constraint}, not a scope change.
 */
public final class KnowledgeGraphLayout {

	private KnowledgeGraphLayout() {
	}

	public enum EntityKind {FEATURE, COMMIT, ASSOCIATION, VARIANT}

	public enum EdgeKind {TOUCHES, SELECTS, INVOLVES, REQUIRES, EXCLUDES, PRODUCES_VARIANT, TOUCHES_COMPUTED}

	private static final double LANE_SPACING = 220;
	private static final double X_SPACING = 140;

	/** One entity's computed place in the graph. */
	public static final class Placement {
		public final EntityKind kind;
		/** Namespaced graph node id, e.g. {@code "F:<featureId>"} - stable across calls for the same entity. */
		public final String id;
		public final String label;
		public final double x;
		public final double y;
		/** {@link at.jku.isse.ecco.tree.Node#countArtifacts()} for an ASSOCIATION placement; -1 otherwise. */
		public final int artifactCount;
		/** True iff this is a FEATURE placement subject to an accepted MANDATORY constraint. */
		public final boolean mandatory;

		private Placement(EntityKind kind, String id, String label, double x, double y, int artifactCount, boolean mandatory) {
			this.kind = kind;
			this.id = id;
			this.label = label;
			this.x = x;
			this.y = y;
			this.artifactCount = artifactCount;
			this.mandatory = mandatory;
		}
	}

	public static final class Edge {
		public final String id;
		public final String sourceId;
		public final String targetId;
		public final EdgeKind kind;

		private Edge(String sourceId, String targetId, EdgeKind kind) {
			this.id = sourceId + "->" + targetId + ":" + kind.name();
			this.sourceId = sourceId;
			this.targetId = targetId;
			this.kind = kind;
		}
	}

	public static final class Snapshot {
		public final List<Placement> nodes;
		public final List<Edge> edges;

		private Snapshot(List<Placement> nodes, List<Edge> edges) {
			this.nodes = nodes;
			this.edges = edges;
		}
	}

	/**
	 * @param enabledKinds                         which entity-type lanes to actually draw; an edge is
	 *                                              included only if both endpoints' lanes are enabled.
	 * @param commitLimit                           how many of the most recent commits (by {@link Commit#getDate()})
	 *                                              are "in scope", or {@code <= 0} for no limit (every commit).
	 *                                              Controls scale, independent of whether the COMMIT lane itself
	 *                                              is enabled: Associations/Variants shown are always derived
	 *                                              from this same windowed commit set, so hiding the Commit lane
	 *                                              doesn't silently uncap Association/Variant count.
	 * @param includeConstraints                   whether to draw accepted REQUIRES/EXCLUDES constraints as
	 *                                              Feature -> Feature edges and mark accepted-MANDATORY features
	 *                                              (see the class javadoc for why these aren't their own node).
	 * @param includeComputedVariantAssociationEdges opt-in: adds a Variant -> Association edge wherever
	 *                                              {@code association.computeCondition().holds(variant.getConfiguration())} -
	 *                                              the one relationship in this graph that isn't a direct object
	 *                                              reference. Off by default (see the class javadoc).
	 */
	public static Snapshot compute(Repository repository, Set<EntityKind> enabledKinds, int commitLimit,
									boolean includeConstraints, boolean includeComputedVariantAssociationEdges) {
		List<Commit> allCommits = new ArrayList<>(repository.getCommits());
		allCommits.sort(Comparator.comparing(Commit::getDate));
		int from = commitLimit <= 0 ? 0 : Math.max(0, allCommits.size() - commitLimit);
		List<Commit> windowedCommits = new ArrayList<>(allCommits.subList(from, allCommits.size()));

		// associations are identity-keyed (not equals/hashCode) - same convention Repository.java's
		// own dirty-tracking and ArtifactGraphView's traversal use, since two distinct associations
		// can otherwise be data-equal.
		Set<Association> touchedAssociations = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Variant> producingVariants = new LinkedHashSet<>();
		for (Commit commit : windowedCommits) {
			touchedAssociations.addAll(commit.getAssociations());
			Configuration configuration = commit.getConfiguration();
			if (configuration != null) {
				Variant variant = repository.getVariant(configuration);
				if (variant != null) {
					producingVariants.add(variant);
				}
			}
		}

		List<Feature> features = new ArrayList<>(repository.getFeatures());
		features.sort(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER));

		List<Constraint> constraints = includeConstraints ? new ArrayList<>(repository.getConstraints()) : List.of();

		Set<String> mandatoryFeatureNames = new HashSet<>();
		for (Constraint constraint : constraints) {
			if (constraint.getKind() == Constraint.Kind.MANDATORY) {
				mandatoryFeatureNames.add(constraint.getFeatureA());
			}
		}

		List<Association> associations = new ArrayList<>(touchedAssociations);
		associations.sort(Comparator.comparing(Association::getId));

		List<Variant> variants = new ArrayList<>(producingVariants);
		variants.sort(Comparator.comparing(Variant::getId));

		List<Placement> nodes = new ArrayList<>();
		List<Edge> edges = new ArrayList<>();
		Map<String, String> featureNodeIdByName = new HashMap<>();
		Map<String, String> associationNodeIdById = new HashMap<>();
		Map<String, String> commitNodeIdById = new HashMap<>();
		Map<String, String> variantNodeIdById = new HashMap<>();

		double featureY = laneY(EntityKind.FEATURE);
		for (int i = 0; i < features.size(); i++) {
			Feature feature = features.get(i);
			String id = "F:" + feature.getId();
			boolean mandatory = mandatoryFeatureNames.contains(feature.getName());
			nodes.add(new Placement(EntityKind.FEATURE, id, feature.getName(), i * X_SPACING, featureY, -1, mandatory));
			featureNodeIdByName.put(feature.getName(), id);
		}

		for (Constraint constraint : constraints) {
			if (constraint.getKind() == Constraint.Kind.MANDATORY) continue; // rendered as a Feature decoration above, not an edge
			EdgeKind edgeKind = constraint.getKind() == Constraint.Kind.REQUIRES ? EdgeKind.REQUIRES : EdgeKind.EXCLUDES;
			String featureAId = featureNodeIdByName.get(constraint.getFeatureA());
			String featureBId = featureNodeIdByName.get(constraint.getFeatureB());
			if (featureAId != null && featureBId != null) {
				edges.add(new Edge(featureAId, featureBId, edgeKind));
			}
		}

		double associationY = laneY(EntityKind.ASSOCIATION);
		for (int i = 0; i < associations.size(); i++) {
			Association association = associations.get(i);
			String id = "A:" + association.getId();
			int artifactCount = association.getRootNode() != null ? association.getRootNode().countArtifacts() : 0;
			nodes.add(new Placement(EntityKind.ASSOCIATION, id, association.getAssociationString(), i * X_SPACING, associationY, artifactCount, false));
			associationNodeIdById.put(association.getId(), id);

			// Association -> Feature ("involves"): the positive features of every module in the
			// association's own presence condition - already-recorded module/counter data (same
			// source Association.computeCondition() always reads), not evaluated against anything
			// external, so this is exactly as "structural/cheap" as Commit/Variant -> Feature above.
			// One association can have several module terms (e.g. surplus/redundant lattice terms -
			// see PresenceConditionMinimizer) that repeat the same feature, so dedupe per association
			// rather than risk GraphStream rejecting a second edge with the same id.
			Set<String> involvedFeatureIds = new HashSet<>();
			for (Module module : association.computeCondition().getModules().keySet()) {
				for (Feature feature : module.getPos()) {
					String featureId = featureNodeIdByName.get(feature.getName());
					if (featureId != null && involvedFeatureIds.add(featureId)) {
						edges.add(new Edge(id, featureId, EdgeKind.INVOLVES));
					}
				}
			}
		}

		double commitY = laneY(EntityKind.COMMIT);
		for (int i = 0; i < windowedCommits.size(); i++) {
			Commit commit = windowedCommits.get(i);
			String id = "C:" + commit.getId();
			nodes.add(new Placement(EntityKind.COMMIT, id, commitLabel(commit), i * X_SPACING, commitY, -1, false));
			commitNodeIdById.put(commit.getId(), id);

			for (Association association : commit.getAssociations()) {
				String associationId = associationNodeIdById.get(association.getId());
				if (associationId != null) {
					edges.add(new Edge(id, associationId, EdgeKind.TOUCHES));
				}
			}
			Configuration configuration = commit.getConfiguration();
			if (configuration != null) {
				for (var featureRevision : configuration.getFeatureRevisions()) {
					String featureId = featureNodeIdByName.get(featureRevision.getFeature().getName());
					if (featureId != null) {
						edges.add(new Edge(id, featureId, EdgeKind.SELECTS));
					}
				}
			}
		}

		double variantY = laneY(EntityKind.VARIANT);
		for (int i = 0; i < variants.size(); i++) {
			Variant variant = variants.get(i);
			String id = "V:" + variant.getId();
			nodes.add(new Placement(EntityKind.VARIANT, id, variantLabel(variant), i * X_SPACING, variantY, -1, false));
			variantNodeIdById.put(variant.getId(), id);

			Configuration configuration = variant.getConfiguration();
			if (configuration != null) {
				for (var featureRevision : configuration.getFeatureRevisions()) {
					String featureId = featureNodeIdByName.get(featureRevision.getFeature().getName());
					if (featureId != null) {
						edges.add(new Edge(id, featureId, EdgeKind.SELECTS));
					}
				}
			}
		}

		// Commit -> Variant ("produces"): a second pass now that both lanes' ids are known.
		for (Commit commit : windowedCommits) {
			Configuration configuration = commit.getConfiguration();
			if (configuration == null) continue;
			Variant variant = repository.getVariant(configuration);
			if (variant == null) continue;
			String commitId = commitNodeIdById.get(commit.getId());
			String variantId = variantNodeIdById.get(variant.getId());
			if (commitId != null && variantId != null) {
				edges.add(new Edge(commitId, variantId, EdgeKind.PRODUCES_VARIANT));
			}
		}

		if (includeComputedVariantAssociationEdges) {
			for (Variant variant : variants) {
				Configuration configuration = variant.getConfiguration();
				if (configuration == null) continue;
				String variantId = variantNodeIdById.get(variant.getId());
				for (Association association : associations) {
					if (association.computeCondition().holds(configuration)) {
						String associationId = associationNodeIdById.get(association.getId());
						edges.add(new Edge(variantId, associationId, EdgeKind.TOUCHES_COMPUTED));
					}
				}
			}
		}

		List<Placement> visibleNodes = new ArrayList<>();
		Set<String> visibleNodeIds = new HashSet<>();
		for (Placement placement : nodes) {
			if (enabledKinds.contains(placement.kind)) {
				visibleNodes.add(placement);
				visibleNodeIds.add(placement.id);
			}
		}
		List<Edge> visibleEdges = new ArrayList<>();
		for (Edge edge : edges) {
			if (visibleNodeIds.contains(edge.sourceId) && visibleNodeIds.contains(edge.targetId)) {
				visibleEdges.add(edge);
			}
		}

		return new Snapshot(visibleNodes, visibleEdges);
	}

	private static double laneY(EntityKind kind) {
		return -kind.ordinal() * LANE_SPACING;
	}

	private static String commitLabel(Commit commit) {
		String id = commit.getId();
		return id.length() <= 7 ? id : id.substring(0, 7);
	}

	private static String variantLabel(Variant variant) {
		String name = variant.getName();
		return name != null && !name.isBlank() ? name : variant.getId();
	}

}
