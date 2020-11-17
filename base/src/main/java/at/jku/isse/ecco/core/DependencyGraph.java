package at.jku.isse.ecco.core;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.collections.impl.factory.Maps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class DependencyGraph {

	private static final int CROSS_REFERENCE_WEIGHT = 1;
	private static final int PARENT_WEIGHT = 1;


	public enum ReferencesResolveMode {
		/**
		 * Artifact references that cannot be resolved using the given set of associations are removed. No new dependencies are introduced.
		 * Parent-Child relations cannot be trimmed and are left unresolved.
		 */
		TRIM_UNRESOLVED_ARTIFACT_REFERENCES,
		/**
		 * Associations that are referenced but not included in the given set of associations are added to the set of associations.
		 */
		INCLUDE_ALL_REFERENCED_ASSOCIATIONS,
		/**
		 * Unresolved references are left unresolved and the dependency is added to the list of unresolved dependencies.
		 */
		LEAVE_REFERENCES_UNRESOLVED
	}


	private Collection<DependencyImpl> dependencies;
	private Collection<DependencyImpl> unresolvedDependencies;

	private Map<Association, Map<Association, DependencyImpl>> dependencyMap;

	private Collection<Association> associations;


	public DependencyGraph() {
		this.dependencies = new ArrayList<>();
		this.unresolvedDependencies = new ArrayList<>();
		this.dependencyMap = Maps.mutable.empty();
		this.associations = new ArrayList<>();
	}

	public DependencyGraph(Collection<? extends Association> associations) {
		this();
		this.compute(associations);
	}

	public DependencyGraph(Collection<? extends Association> associations, ReferencesResolveMode referencesResolveMode) {
		this();
		this.compute(associations, referencesResolveMode);
	}


	public Collection<Association> getAssociations() {
		return new ArrayList<>(this.associations);
	}


	public void compute(Collection<? extends Association> associations) {
		this.compute(associations, ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED);
	}

	public void compute(Collection<? extends Association> associations, ReferencesResolveMode referencesResolveMode) {
		this.dependencies.clear();
		this.unresolvedDependencies.clear();
		this.dependencyMap.clear();
		this.associations.clear();
		this.associations.addAll(associations);

		for (Association association : associations) {
			this.computeRec(association, association.getRootNode(), referencesResolveMode);
		}
	}

	private void computeRec(Association fromA, Node node, ReferencesResolveMode referencesResolveMode) {
		if (node.isUnique() && node.getArtifact() != null) {
			// cross references
			Iterator<? extends ArtifactReference> it = node.getArtifact().getUses().iterator();
			while (it.hasNext()) {
				ArtifactReference ar = it.next();

				Association toA = ar.getTarget().getContainingNode().getContainingAssociation();

				if (toA != null) {
					if (this.associations.contains(toA) || referencesResolveMode != ReferencesResolveMode.TRIM_UNRESOLVED_ARTIFACT_REFERENCES) {
						if (!this.associations.contains(toA) && referencesResolveMode == ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS) {
							this.associations.add(toA);
						}
						if (fromA != toA) {
							Map<Association, DependencyImpl> fromDependencyMap = this.dependencyMap.computeIfAbsent(fromA, k -> Maps.mutable.empty());
							DependencyImpl dependency = fromDependencyMap.get(toA);
							if (dependency == null) {
								dependency = new DependencyImpl();
								dependency.setFrom(fromA);
								dependency.setTo(toA);
								fromDependencyMap.put(toA, dependency);
								if (this.associations.contains(toA))
									this.dependencies.add(dependency);
								else
									this.unresolvedDependencies.add(dependency);
							}

							dependency.setWeight(dependency.getWeight() + CROSS_REFERENCE_WEIGHT);
						}
					} else {
						ar.getTarget().getUsedBy().remove(ar);
						it.remove();
					}
				} else {
					throw new EccoException("Artifacts must be contained in an association.");
				}
			}

			// parent
			if (node.getParent() != null && node.getParent().getArtifact() != null) {
				Association parentA = node.getParent().getArtifact().getContainingNode().getContainingAssociation();

				if (parentA != null) {
					if (fromA != parentA) {
						Map<Association, DependencyImpl> fromDependencyMap = this.dependencyMap.computeIfAbsent(fromA, k -> Maps.mutable.empty());
						DependencyImpl dependency = fromDependencyMap.get(parentA);
						if (dependency == null) {
							dependency = new DependencyImpl();
							dependency.setFrom(fromA);
							dependency.setTo(parentA);
							fromDependencyMap.put(parentA, dependency);
							if (this.associations.contains(parentA))
								this.dependencies.add(dependency);
							else
								this.unresolvedDependencies.add(dependency);
						}

						dependency.setWeight(dependency.getWeight() + PARENT_WEIGHT);
					}
				} else {
					throw new EccoException("Artifacts must be contained in an association.");
				}
			}
		}

		for (Node child : node.getChildren()) {
			this.computeRec(fromA, child, referencesResolveMode);
		}
	}


	public Dependency getDependency(Association from, Association to) {
		for (Dependency dependency : this.dependencies) {
			if (dependency.getFrom() == from && dependency.getTo() == to)
				return dependency;
		}
		return null;
	}

	public Collection<Dependency> getDependencies() {
		return new ArrayList<>(this.dependencies);
	}


	public Dependency getUnresolvedDependency(Association from, Association to) {
		for (Dependency dependency : this.unresolvedDependencies) {
			if (dependency.getFrom() == from && dependency.getTo() == to)
				return dependency;
		}
		return null;
	}

	public Collection<Dependency> getUnresolvedDependencies() {
		return new ArrayList<>(this.unresolvedDependencies);
	}


	public String getGMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append("graph [\n");
		sb.append("\tdirected 1\n");

		for (Association association : this.associations) {
			sb.append("\tnode [\n");
			sb.append("\t\tid ").append(association.getId()).append("\n");
			sb.append("\t\tsize ").append(association.getRootNode().countArtifacts()).append("\n");
			sb.append("\t]\n");
		}

		for (Dependency dependency : this.dependencies) {
			sb.append("\tedge [\n");
			sb.append("\t\tsource ").append(dependency.getFrom().getId()).append("\n");
			sb.append("\t\ttarget ").append(dependency.getTo().getId()).append("\n");
			sb.append("\t\tlabel ").append(dependency.getWeight()).append("\n");
			sb.append("\t\tweight ").append(dependency.getWeight()).append("\n");
			sb.append("\t]\n");
		}

		sb.append("]\n");

		return sb.toString();
	}


	public interface Dependency {
		public int getWeight();

		public Association getFrom();

		public Association getTo();
	}

	public class DependencyImpl implements Dependency {
		private int weight;
		private Association from;
		private Association to;

		public int getWeight() {
			return weight;
		}

		public void setWeight(int weight) {
			this.weight = weight;
		}

		public Association getFrom() {
			return from;
		}

		public void setFrom(Association from) {
			this.from = from;
		}

		public Association getTo() {
			return to;
		}

		public void setTo(Association to) {
			this.to = to;
		}
	}

}
