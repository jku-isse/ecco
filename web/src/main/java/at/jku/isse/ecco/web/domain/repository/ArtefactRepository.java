package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.web.domain.model.ArtefactGraphModel;
import at.jku.isse.ecco.web.domain.model.ArtefactTreeModel;
import at.jku.isse.ecco.web.domain.model.ArtefactTreeNodeModel;
import at.jku.isse.ecco.web.domain.model.Artefactgraph.ArtefactgraphEdge;
import at.jku.isse.ecco.web.domain.model.Artefactgraph.ArtefactgraphNode;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class ArtefactRepository extends AbstractRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtefactRepository.class);

    private EccoApplication application;

    public ArtefactRepository() {
    }

    public ArtefactRepository(EccoApplication eccoApplication) {
        this.application = eccoApplication;
    }

    public ArtefactTreeModel getArtifactsByAssociation(AssociationModel[] givenAssociations) {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Association> associationCollection = eccoService.getRepository().getAssociations();

        ArtefactTreeModel artefactTreeModel = new ArtefactTreeModel();
        LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
        for (Association association : associationCollection) {
            for (AssociationModel walkerAssociationModel : givenAssociations) {
                if (association.getId().equals(walkerAssociationModel.getAssociationID())) {
                    compRootNode.addOrigNode(association.getRootNode());
                }
            }
        }

        ArtefactTreeNodeModel rootNode = new ArtefactTreeNodeModel();
        if ((compRootNode.getArtifact() != null)) {
            rootNode.setArtefactData(compRootNode.getArtifact().getData().toString());
            rootNode.setOrdered(compRootNode.getArtifact().isOrdered());
            rootNode.setSequenceNumber(compRootNode.getArtifact().getSequenceNumber());
            rootNode.setCorrespondingAssociation(compRootNode.getArtifact().getContainingNode().getContainingAssociation().getId());
        }
        rootNode.setUnique(compRootNode.isUnique());
        rootNode.setAtomic(compRootNode.isAtomic());

        for (Node associationChildNode : compRootNode.getChildren()) {
            rootNode.addChildNode(this.traverseArtifactTree(associationChildNode));
        }
        artefactTreeModel.setRootNode(rootNode);

        return artefactTreeModel;
    }

    public ArtefactGraphModel getArtefactgraphFromAllAssociations() {
        try {
            EccoService eccoService = this.application.getEccoService();
            Collection<? extends Association> associationCollection = eccoService.getRepository().getAssociations();

            LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
            for (Association association : associationCollection) {
                compRootNode.addOrigNode(association.getRootNode());
            }

            return this.parseTreeToGraph(compRootNode, null);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
            e.printStackTrace();
        }
        return new ArtefactGraphModel(null, null);
    }

    private ArtefactGraphModel parseTreeToGraph(
            Node givenRootOrChildNode,
            ArtefactgraphNode parentNodeToGivenRootOrChildNode
    ) {
        ArrayList<ArtefactgraphNode> nodeList = new ArrayList<>();
        ArrayList<ArtefactgraphEdge> edgeList = new ArrayList<>();
        String nodeUUid = UUID.randomUUID().toString().replace("-", "");
        ArtefactgraphNode artefactNode = new ArtefactgraphNode();
        artefactNode.setId(nodeUUid);
        if (givenRootOrChildNode.getArtifact() != null) {
            artefactNode.setName(givenRootOrChildNode.getArtifact().getData().toString());
        }

        nodeList.add(artefactNode);

        if (parentNodeToGivenRootOrChildNode != null) {
            String edgeUID =  UUID.randomUUID().toString().replace("-", "");
            edgeList.add(new ArtefactgraphEdge(edgeUID, parentNodeToGivenRootOrChildNode.getId(), artefactNode.getId()));
        }

        for (Node childNode : givenRootOrChildNode.getChildren()) {
            ArtefactGraphModel artefactGraphModel = this.parseTreeToGraph(childNode, artefactNode);
            nodeList.addAll(artefactGraphModel.getArtefactgraphNodeList());
            edgeList.addAll(artefactGraphModel.getArtefactgraphEdgeList());
        }
        return new ArtefactGraphModel(nodeList, edgeList);

    }

    private ArtefactTreeNodeModel traverseArtifactTree(Node givenRootNode) {
        ArtefactTreeNodeModel childNode = new ArtefactTreeNodeModel();
        if (givenRootNode.getArtifact() != null) {
            childNode.setArtefactData(givenRootNode.getArtifact().getData().toString());
            childNode.setOrdered(givenRootNode.getArtifact().isOrdered());
            childNode.setSequenceNumber(givenRootNode.getArtifact().getSequenceNumber());
            childNode.setCorrespondingAssociation(givenRootNode.getArtifact().getContainingNode().getContainingAssociation().getId());
        }
        childNode.setUnique(givenRootNode.isUnique());
        childNode.setAtomic(givenRootNode.isAtomic());

        for (Node associationChildNode : givenRootNode.getChildren()) {
            childNode.addChildNode(this.traverseArtifactTree(associationChildNode));
        }

        return childNode;
    }
}
