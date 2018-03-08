package at.jku.isse.ecco.storage.perst.dao;

import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.perst.artifact.PerstArtifact;
import at.jku.isse.ecco.storage.perst.artifact.PerstArtifactReference;
import at.jku.isse.ecco.storage.perst.core.PerstAssociation;
import at.jku.isse.ecco.storage.perst.counter.PerstAssociationCounter;
import at.jku.isse.ecco.storage.perst.feature.PerstFeature;
import at.jku.isse.ecco.storage.perst.feature.PerstFeatureRevision;
import at.jku.isse.ecco.storage.perst.repository.PerstRepository;
import at.jku.isse.ecco.storage.perst.sg.PerstSequenceGraph;
import at.jku.isse.ecco.storage.perst.tree.PerstNode;
import at.jku.isse.ecco.storage.perst.tree.PerstRootNode;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public class PerstRepositoryDao extends PerstAbstractGenericDao implements RepositoryDao {

	private final PerstEntityFactory entityFactory;

	@Inject
	public PerstRepositoryDao(PerstTransactionStrategy transactionStrategy, final PerstEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public Repository.Op load() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.getRepository();
	}

	@Override
	public void store(Repository.Op repository) {
		PerstRepository perstRepository = (PerstRepository) repository;

		perstRepository.store();

		// features
		for (PerstFeature feature : perstRepository.getFeatures()) {
			feature.store();
			for (PerstFeatureRevision featureVersion : feature.getRevisions()) {
				featureVersion.store();
			}
		}

		// modules
		// TODO: save modules here

		// associations
		for (PerstAssociation association : perstRepository.getAssociations()) {
			association.store();
			this.saveCounter(association.getCounter());
			this.saveNode(association.getRootNode());
		}

	}


	private AssociationCounter saveCounter(final AssociationCounter entity) {
		checkNotNull(entity);

		final PerstAssociationCounter associationCounter = (PerstAssociationCounter) entity;

		// TODO: save counter here
		associationCounter.storeRecursively();

		return associationCounter;
	}

	private Node saveNode(final Node entity) {
		checkNotNull(entity);

		if (entity instanceof PerstNode) {
			//this.openDatabase();
			PerstNode node = (PerstNode) entity;
			node.store();
			for (Node child : node.getChildren()) {
				this.saveNode(child);
			}
			if (node.getArtifact() != null) {
				// store artifact itself
				((PerstArtifact) node.getArtifact()).store();
				// store sequence graph
				if (node.getArtifact().getSequenceGraph() != null && node.getArtifact().getSequenceGraph() instanceof PerstSequenceGraph) {
					((PerstSequenceGraph) node.getArtifact().getSequenceGraph()).storeRecursively();
				}
				// store artifact references
				for (ArtifactReference ref : node.getArtifact().getUses())
					if (ref instanceof PerstArtifactReference)
						((PerstArtifactReference) ref).store();
				for (ArtifactReference ref : node.getArtifact().getUsedBy())
					if (ref instanceof PerstArtifactReference)
						((PerstArtifactReference) ref).store();
			}
			//this.closeDatabase();
			return node;
		} else if (entity instanceof PerstRootNode) {
			//this.openDatabase();
			PerstRootNode node = (PerstRootNode) entity;
			node.store();
			for (Node child : node.getChildren()) {
				this.saveNode(child);
			}
			if (node.getArtifact() != null) {
				((PerstArtifact) node.getArtifact()).store();
				if (node.getArtifact().getSequenceGraph() instanceof PerstSequenceGraph) {
					((PerstSequenceGraph) node.getArtifact().getSequenceGraph()).storeRecursively();
				}
			}
			//this.closeDatabase();
			return node;
		}

		return null;
	}

}
