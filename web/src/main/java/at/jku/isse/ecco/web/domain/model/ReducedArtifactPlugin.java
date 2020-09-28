package at.jku.isse.ecco.web.domain.model;

public class ReducedArtifactPlugin {

    private String pluginID;
    private String name;
    private String description;

    public ReducedArtifactPlugin() {

    }

    public ReducedArtifactPlugin(String pluginID, String name, String description) {
        this.pluginID = pluginID;
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return null;
    }

    public String getDescription() {
        return null;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPluginID() {
        return pluginID;
    }

    public void setPluginID(String pluginID) {
        this.pluginID = pluginID;
    }
}
