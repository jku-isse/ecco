package at.jku.isse.ecco.web.domain.model.Artefactgraph;

public class ArtefactgraphEdge {

    private String id;
    private String source;
    private String target;

    public ArtefactgraphEdge(String id, String source, String target) {
        this.id = id;
        this.source = source;
        this.target = target;
    }

    public ArtefactgraphEdge(ArtefactgraphEdge artefactgraphEdge) {
        this.id = artefactgraphEdge.id;
        this.source = artefactgraphEdge.source;
        this.target = artefactgraphEdge.target;
    }

    public ArtefactgraphEdge() {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        ArtefactgraphEdge comparedEdge = (ArtefactgraphEdge) obj;

        return (id == comparedEdge.id
                || (id != null && id.equals(comparedEdge.getId())))
                && (source == comparedEdge.source
                || (source != null && source.equals(comparedEdge.getSource())))
                && (target == comparedEdge.target
                || (target != null && target.equals(comparedEdge.getTarget())));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + ((target == null) ? 0 : target.hashCode());
        return result;
    }
}
