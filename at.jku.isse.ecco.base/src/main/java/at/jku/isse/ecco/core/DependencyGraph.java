package at.jku.isse.ecco.core;

import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

public class DependencyGraph {

	private static final int CROSS_REFERENCE_WEIGHT = 1;
	private static final int PARENT_WEIGHT = 1;


	public enum ReferencesResolveMode {
		TRIM_UNRESOLVED_ARTIFACT_REFERENCES, INCLUDE_ALL_REFERENCED_ASSOCIATIONS, LEAVE_REFERENCES_UNRESOLVED
	}


	private Collection<DependencyImpl> dependencies = new ArrayList<>();

	private Map<Association, Map<Association, DependencyImpl>> dependencyMap = new HashMap<>();

	private int unresolvedDependenciesWeight = 0;

	private Collection<Association> associations = new ArrayList<>();


	public DependencyGraph() {

	}

	public DependencyGraph(Collection<Association> associations) {
		this.compute(associations);
	}

	public DependencyGraph(Collection<Association> associations, ReferencesResolveMode referencesResolveMode) {
		this.compute(associations, referencesResolveMode);
	}


	public Collection<Association> getAssociations() {
		return new ArrayList<>(this.associations);
	}


	public void compute(Collection<Association> associations) {
		this.compute(associations, ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED);
	}

	public void compute(Collection<Association> associations, ReferencesResolveMode referencesResolveMode) {
		this.dependencies.clear();
		this.dependencyMap.clear();
		this.unresolvedDependenciesWeight = 0;
		this.associations.clear();
		this.associations.addAll(associations);

		for (Association association : this.associations) {
			this.computeRec(association, association.getRootNode(), referencesResolveMode);
		}
	}

	private void computeRec(Association fromA, Node node, ReferencesResolveMode referencesResolveMode) {
		if (node.isUnique() && node.getArtifact() != null) {
			// cross references
			Iterator<ArtifactReference> it = node.getArtifact().getUses().iterator();
			while (it.hasNext()) {
				ArtifactReference ar = it.next();

				Association toA = ar.getTarget().getContainingNode().getContainingAssociation();

				if (toA != null && (this.associations.contains(toA) || referencesResolveMode == ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS)) {
					if (!this.associations.contains(toA) && referencesResolveMode == ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS) {
						this.associations.add(toA);

						this.unresolvedDependenciesWeight += CROSS_REFERENCE_WEIGHT;
					}
					if (fromA != toA) {
						Map<Association, DependencyImpl> fromDependencyMap = this.dependencyMap.get(fromA);
						if (fromDependencyMap == null) {
							fromDependencyMap = new HashMap<>();
							this.dependencyMap.put(fromA, fromDependencyMap);
						}
						DependencyImpl dependency = fromDependencyMap.get(toA);
						if (dependency == null) {
							dependency = new DependencyImpl();
							fromDependencyMap.put(toA, dependency);
						}

						dependency.setWeight(dependency.getWeight() + CROSS_REFERENCE_WEIGHT);
					}
				} else {
					if (referencesResolveMode == ReferencesResolveMode.TRIM_UNRESOLVED_ARTIFACT_REFERENCES) {
						ar.getTarget().getUsedBy().remove(ar);
						it.remove();
					}

					this.unresolvedDependenciesWeight += CROSS_REFERENCE_WEIGHT;
				}
			}

			// parent
			if (node.getParent() != null) {
				Association parentA = node.getParent().getArtifact().getContainingNode().getContainingAssociation();

				if (parentA != null && this.associations.contains(parentA)) {
					if (fromA != parentA) {
						Map<Association, DependencyImpl> fromDependencyMap = this.dependencyMap.get(fromA);
						if (fromDependencyMap == null) {
							fromDependencyMap = new HashMap<>();
							this.dependencyMap.put(fromA, fromDependencyMap);
						}
						DependencyImpl dependency = fromDependencyMap.get(parentA);
						if (dependency == null) {
							dependency = new DependencyImpl();
							fromDependencyMap.put(parentA, dependency);
						}

						dependency.setWeight(dependency.getWeight() + PARENT_WEIGHT);
					}
				} else {
					this.unresolvedDependenciesWeight += PARENT_WEIGHT;
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

	public int getUnresolvedDependencyWeight() {
		return this.unresolvedDependenciesWeight;
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
