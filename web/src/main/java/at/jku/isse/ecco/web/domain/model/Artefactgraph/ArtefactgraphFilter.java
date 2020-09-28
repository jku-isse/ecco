package at.jku.isse.ecco.web.domain.model.Artefactgraph;

public class ArtefactgraphFilter {
    private int maxChildCount;
    private String nodeID;

    public int getMaxChildCount() {
        return maxChildCount;
    }

    public void setMaxChildCount(int maxChildCount) {
        this.maxChildCount = maxChildCount;
    }

    public String getNodeID() {
        return nodeID;
    }

    public void setNodeID(String nodeID) {
        this.nodeID = nodeID;
    }
}
