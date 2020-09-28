package at.jku.isse.ecco.web.domain.model;

public class ArtifactsPerDepth {

    private int depth;
    private int numberOfArtifacts;

    public ArtifactsPerDepth(int numberOfArtifacts, int depth) {
        this.numberOfArtifacts = numberOfArtifacts;
        this.depth = depth;
    }

    public ArtifactsPerDepth() {

    }


    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getNumberOfArtifacts() {
        return numberOfArtifacts;
    }

    public void setNumberOfArtifacts(int numberOfArtifacts) {
        this.numberOfArtifacts = numberOfArtifacts;
    }
}
