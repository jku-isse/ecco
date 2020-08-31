package at.jku.isse.ecco.web.domain.model.Artefactgraph;

public class ArtefactgraphNode {

    public static final int DEFAULT_NODE_SYMBOLSIZE = 20;
    public static final int SYMBOLSIZE_MULTIPLIER = 2;

    private String id;
    private String name;
    private int symbolSize = DEFAULT_NODE_SYMBOLSIZE;

    public ArtefactgraphNode() {

    }

    public ArtefactgraphNode(ArtefactgraphNode artefactgraphNode) {
        this.id = artefactgraphNode.id;
        this.name = artefactgraphNode.name;
    }

    public ArtefactgraphNode(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        ArtefactgraphNode comparedNode = (ArtefactgraphNode) obj;

        return (id == comparedNode.id
                || (id != null && id.equals(comparedNode.getId())))
                && (name == comparedNode.name
                || (name != null && name.equals(comparedNode.getName())));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    public int getSymbolSize() {
        return symbolSize;
    }

    public void setSymbolSize(int symbolSize) {
        this.symbolSize = symbolSize;
    }
}
