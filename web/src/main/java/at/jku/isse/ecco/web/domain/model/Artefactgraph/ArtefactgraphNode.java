package at.jku.isse.ecco.web.domain.model.Artefactgraph;

public class ArtefactgraphNode {

    private String id;
    private String name;

    public ArtefactgraphNode() {

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
}
