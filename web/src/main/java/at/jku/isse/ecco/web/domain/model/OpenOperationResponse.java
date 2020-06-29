package at.jku.isse.ecco.web.domain.model;

public class OpenOperationResponse extends OperationResponse {

    private ReducedArtifactPlugin[] artifactPlugins;

    public OpenOperationResponse (
            boolean eccoServiceIsInitialized,
            ReducedArtifactPlugin[] artifactplugins
    ) {
        this.setEccoServiceIsInitialized(eccoServiceIsInitialized);
        this.setArtifactPlugins(artifactplugins);
    }

    public OpenOperationResponse () {

    }
    public ReducedArtifactPlugin[] getArtifactPlugins() {
        return artifactPlugins;
    }

    public void setArtifactPlugins(ReducedArtifactPlugin[] artifactPlugins) {
        this.artifactPlugins = artifactPlugins;
    }
}
