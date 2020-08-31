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
import java.util.stream.Collectors;

public class ArtefactRepository extends AbstractRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtefactRepository.class);

    private EccoApplication application;

    public ArtefactRepository() {}

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

    public ArtefactGraphModel getArtefactgraphFromAllAssociations(int maxChildCount) {
        try {
            EccoService eccoService = this.application.getEccoService();
            Collection<? extends Association> associationCollection = eccoService.getRepository().getAssociations();

            LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
            for (Association association : associationCollection) {
                compRootNode.addOrigNode(association.getRootNode());
            }

            ArtefactGraphModel backendGraph = this.parseTreeToGraph(compRootNode, null);
            if (this.application.getBackendGraph() == null) {
                this.application.setBackendGraph(backendGraph);
            }

            this.generateFrontendGraphWithFilter(maxChildCount);

            return this.application.getFrontendGraph();
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
            e.printStackTrace();
        }

        return new ArtefactGraphModel(null, null);
    }

    private void generateFrontendGraphWithFilter(int maxChildCount) {
        ArtefactGraphModel backendGraph = this.application.getBackendGraph();

        ArrayList<ArtefactgraphNode> copyOfBackendGraphNodelist = (ArrayList<ArtefactgraphNode>) backendGraph.getArtefactgraphNodeList().stream().map(ArtefactgraphNode::new).collect(Collectors.toList());
        ArrayList<ArtefactgraphEdge> copyOfBackendGraphEdgelist = (ArrayList<ArtefactgraphEdge>) backendGraph.getArtefactgraphEdgeList().stream().map(ArtefactgraphEdge::new).collect(Collectors.toList());

        ArrayList<ArtefactgraphNode> nodesToRemoveFromCompleteGraph = new ArrayList<>();
        ArrayList<ArtefactgraphEdge> edgesToRemoveFromCompleteGraph = new ArrayList<>();

        copyOfBackendGraphNodelist.forEach((ArtefactgraphNode specificNode) -> {
            ArrayList<ArtefactgraphEdge> edgesForSpecificNode = this.findAllEdgesForNodeInsideBackendGraph(specificNode);
            if (edgesForSpecificNode.size() > maxChildCount) {
                specificNode.setSymbolSize(specificNode.getSymbolSize() * ArtefactgraphNode.SYMBOLSIZE_MULTIPLIER);

                ArtefactGraphModel subGraphOfNode = this.findAllNodesAndEdgesToRemoveInsideBackendgraph(specificNode.getId());

                nodesToRemoveFromCompleteGraph.addAll(subGraphOfNode.getArtefactgraphNodeList());
                edgesToRemoveFromCompleteGraph.addAll(subGraphOfNode.getArtefactgraphEdgeList());
            }
        });
        copyOfBackendGraphEdgelist.removeAll(edgesToRemoveFromCompleteGraph);
        copyOfBackendGraphNodelist.removeAll(nodesToRemoveFromCompleteGraph);

        this.application.setFrontendGraph(new ArtefactGraphModel(copyOfBackendGraphNodelist, copyOfBackendGraphEdgelist));
    }

    private ArtefactGraphModel findAllNodesAndEdgesToRemoveInsideBackendgraph(String nodeId) {
        ArrayList<ArtefactgraphEdge> edgesForSpecificNode = this.findAllEdgesForNodeInsideBackendGraph(nodeId);
        ArrayList<ArtefactgraphNode> childNodesConnectedToSpecificNode = this.findAllNodesFromEdgeListInsideBackendGraph(edgesForSpecificNode);

        ArrayList<ArtefactgraphEdge> egdesOfChildNodes = (ArrayList<ArtefactgraphEdge>) edgesForSpecificNode.stream().map(ArtefactgraphEdge::new).collect(Collectors.toList());
        ArrayList<ArtefactgraphNode> nodesOfEdgesOfChildnodes = (ArrayList<ArtefactgraphNode>) childNodesConnectedToSpecificNode.stream().map(ArtefactgraphNode::new).collect(Collectors.toList());

        for (ArtefactgraphNode childNode : childNodesConnectedToSpecificNode) {

            ArtefactGraphModel subGraph = this.findAllNodesAndEdgesToRemoveInsideBackendgraph(childNode.getId());

            egdesOfChildNodes.addAll(subGraph.getArtefactgraphEdgeList());
            nodesOfEdgesOfChildnodes.addAll(subGraph.getArtefactgraphNodeList());
        }

        return new ArtefactGraphModel(nodesOfEdgesOfChildnodes, egdesOfChildNodes);
    }

    private ArtefactGraphModel findAllNodesAndEdgesToRemoveInsideFrontentGraph(String nodeID) {
        ArrayList<ArtefactgraphEdge> edgesToChildNodesFromClickedNode = this.findAllEdgesForNodeInsideFrontendGraph(nodeID);
        ArrayList<ArtefactgraphNode> childNodesFromEdges = this.findAllNodesFromEdgeListInsideFrontendGraph(edgesToChildNodesFromClickedNode);

        ArrayList<ArtefactgraphEdge> copiedEdgesToChildNodes = (ArrayList<ArtefactgraphEdge>) edgesToChildNodesFromClickedNode.stream().map(ArtefactgraphEdge::new).collect(Collectors.toList());
        ArrayList<ArtefactgraphNode> nodesOfEdgesOfChildnodes = (ArrayList<ArtefactgraphNode>) childNodesFromEdges.stream().map(ArtefactgraphNode::new).collect(Collectors.toList());

        for (ArtefactgraphNode childNode : childNodesFromEdges) {
            ArtefactGraphModel subGraph = this.findAllNodesAndEdgesToRemoveInsideFrontentGraph(childNode.getId());

            copiedEdgesToChildNodes.addAll(subGraph.getArtefactgraphEdgeList());
            nodesOfEdgesOfChildnodes.addAll(subGraph.getArtefactgraphNodeList());
        }

        return new ArtefactGraphModel(nodesOfEdgesOfChildnodes, copiedEdgesToChildNodes);
    }

    private ArrayList<ArtefactgraphNode> findAllNodesFromEdgeListInsideFrontendGraph(ArrayList<ArtefactgraphEdge> edgeList) {
        ArrayList<ArtefactgraphNode> copyOfBackendGraphNodelist = (ArrayList<ArtefactgraphNode>) this.application.getFrontendGraph().getArtefactgraphNodeList().stream().map(ArtefactgraphNode::new).collect(Collectors.toList());
        ArrayList<ArtefactgraphNode> targetNodesForEdgelist = new ArrayList<>();
        edgeList.forEach((ArtefactgraphEdge edgeToChildNode) -> {
            copyOfBackendGraphNodelist.forEach((ArtefactgraphNode tmpNode) -> {
                if (edgeToChildNode.getTarget().equals(tmpNode.getId())) {
                    targetNodesForEdgelist.add(tmpNode);
                }
            });
        });
        return targetNodesForEdgelist;
    }

    private ArrayList<ArtefactgraphNode> findAllNodesFromEdgeListInsideBackendGraph(ArrayList<ArtefactgraphEdge> edgeList) {
        ArrayList<ArtefactgraphNode> copyOfBackendGraphNodelist = (ArrayList<ArtefactgraphNode>) this.application.getBackendGraph().getArtefactgraphNodeList().stream().map(ArtefactgraphNode::new).collect(Collectors.toList());
        ArrayList<ArtefactgraphNode> targetNodesForEdgelist = new ArrayList<>();
        edgeList.forEach((ArtefactgraphEdge edgeToChildNode) -> {
            copyOfBackendGraphNodelist.forEach((ArtefactgraphNode tmpNode) -> {
                if (edgeToChildNode.getTarget().equals(tmpNode.getId())) {
                    targetNodesForEdgelist.add(tmpNode);
                }
            });
        });
        return targetNodesForEdgelist;
    }

    private ArrayList<ArtefactgraphEdge> findAllEdgesForNodeInsideBackendGraph(ArtefactgraphNode givenNode) {
        return this.findAllEdgesForNodeInsideBackendGraph(givenNode.getId());
    }

    private ArrayList<ArtefactgraphEdge> findAllEdgesForNodeInsideBackendGraph(String givenNodeId) {
        ArrayList<ArtefactgraphEdge> copyOfBackendGraphEdgelist = (ArrayList<ArtefactgraphEdge>) this.application.getBackendGraph().getArtefactgraphEdgeList().stream().map(ArtefactgraphEdge::new).collect(Collectors.toList());
        copyOfBackendGraphEdgelist.removeIf((ArtefactgraphEdge artefactEdge) -> (!artefactEdge.getSource().equals(givenNodeId)));
        return copyOfBackendGraphEdgelist;
    }

    private ArrayList<ArtefactgraphEdge> findAllEdgesForNodeInsideFrontendGraph(ArtefactgraphNode givenNode) {
        return this.findAllEdgesForNodeInsideFrontendGraph(givenNode.getId());
    }

    private ArrayList<ArtefactgraphEdge> findAllEdgesForNodeInsideFrontendGraph(String givenNodeId) {
        ArrayList<ArtefactgraphEdge> copyOfBackendGraphEdgelist = (ArrayList<ArtefactgraphEdge>) this.application.getFrontendGraph().getArtefactgraphEdgeList().stream().map(ArtefactgraphEdge::new).collect(Collectors.toList());
        copyOfBackendGraphEdgelist.removeIf((ArtefactgraphEdge artefactEdge) -> (!artefactEdge.getSource().equals(givenNodeId)));
        return copyOfBackendGraphEdgelist;
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

    public ArtefactGraphModel getUpdatedFrontendGraphByNodeID(String nodeID, int maxChildCount) {
        ArtefactgraphNode clickedNodeInFrontendGraph = this.findClickedNodeInListForFrontendGraph(nodeID);
        LOGGER.info("Node existiert mit...: " + "(" + clickedNodeInFrontendGraph.getId() + "/" + clickedNodeInFrontendGraph.getName() + "/" + clickedNodeInFrontendGraph.getSymbolSize() + ")");
        ArrayList<ArtefactgraphEdge> edgesToChildNodesForClickedNode = this.findAllEdgesForNodeInsideFrontendGraph(clickedNodeInFrontendGraph);

        if (edgesToChildNodesForClickedNode.size() > 0 && clickedNodeInFrontendGraph.getSymbolSize() == ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE) {
            //Rekursiv Kindknoten und Kanten entfernen, die in diesem Subbaum enthalten sind...
            LOGGER.info("Knoten hat zwar Kinder, aber besitzt Standardgöße: KNOTEN ENTFERNEN!");
            ArtefactGraphModel subGraphToRemove = this.findAllNodesAndEdgesToRemoveInsideFrontentGraph(clickedNodeInFrontendGraph.getId());

            clickedNodeInFrontendGraph.setSymbolSize(ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE * ArtefactgraphNode.SYMBOLSIZE_MULTIPLIER);
            this.application.getFrontendGraph().getArtefactgraphEdgeList().removeAll(subGraphToRemove.getArtefactgraphEdgeList());
            this.application.getFrontendGraph().getArtefactgraphNodeList().removeAll(subGraphToRemove.getArtefactgraphNodeList());
            return this.application.getFrontendGraph();
        }

        if (edgesToChildNodesForClickedNode.size() == 0 && clickedNodeInFrontendGraph.getSymbolSize() == ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE) {
            return this.application.getFrontendGraph();
        }

        if (clickedNodeInFrontendGraph.getSymbolSize() > ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE) {
            ArrayList<ArtefactgraphEdge> edgesToChildNodes = this.findAllEdgesForNodeInsideBackendGraph(clickedNodeInFrontendGraph);
            ArrayList<ArtefactgraphNode> childNodesConnectedByEdgesFromClickedNode = this.findAllNodesFromEdgeListInsideBackendGraph(edgesToChildNodes);
            for (ArtefactgraphNode walkerNode : childNodesConnectedByEdgesFromClickedNode) {
                if (this.findAllEdgesForNodeInsideBackendGraph(walkerNode).size() > 0) {
                    walkerNode.setSymbolSize(walkerNode.getSymbolSize() * ArtefactgraphNode.SYMBOLSIZE_MULTIPLIER);
                }
            }
            clickedNodeInFrontendGraph.setSymbolSize(ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE);
            this.application.getFrontendGraph().getArtefactgraphNodeList().addAll(childNodesConnectedByEdgesFromClickedNode);
            this.application.getFrontendGraph().getArtefactgraphEdgeList().addAll(edgesToChildNodes);
        }

        return this.application.getFrontendGraph();
    }

    private ArtefactgraphNode findClickedNodeInListForFrontendGraph(String nodeID) {
        ArtefactgraphNode clickedNode = null;
        ArrayList<ArtefactgraphNode> currentFrontendNodeList = this.application.getFrontendGraph().getArtefactgraphNodeList();
        for (ArtefactgraphNode walkerNode : currentFrontendNodeList) {
            if (walkerNode.getId().equals(nodeID)) {
                clickedNode = walkerNode;
            }
        }
        return clickedNode;
    }
}
