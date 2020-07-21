package at.jku.isse.ecco.web.domain.model;

import at.jku.isse.ecco.web.domain.model.Artefactgraph.ArtefactgraphEdge;
import at.jku.isse.ecco.web.domain.model.Artefactgraph.ArtefactgraphNode;

import java.util.ArrayList;

public class ArtefactGraphModel {

    private ArrayList<ArtefactgraphNode> artefactgraphNodeList;
    private ArrayList<ArtefactgraphEdge> artefactgraphEdgeList;

    public ArtefactGraphModel() {

    }

    public ArtefactGraphModel(ArrayList<ArtefactgraphNode> artefactgraphNodeList, ArrayList<ArtefactgraphEdge> artefactgraphEdgeList) {
        this.artefactgraphNodeList = artefactgraphNodeList;
        this.artefactgraphEdgeList = artefactgraphEdgeList;
    }

    public ArrayList<ArtefactgraphEdge> getArtefactgraphEdgeList() {
        return artefactgraphEdgeList;
    }

    public void setArtefactgraphEdgeList(ArrayList<ArtefactgraphEdge> artefactgraphEdgeList) {
        this.artefactgraphEdgeList = artefactgraphEdgeList;
    }

    public ArrayList<ArtefactgraphNode> getArtefactgraphNodeList() {
        return artefactgraphNodeList;
    }

    public void setArtefactgraphNodeList(ArrayList<ArtefactgraphNode> artefactgraphNodeList) {
        this.artefactgraphNodeList = artefactgraphNodeList;
    }
}
