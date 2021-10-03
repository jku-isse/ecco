package at.jku.isse.ecco.web.domain.model;

public class FeatureVersionModel {

    private String version;
    private String description;

    public FeatureVersionModel() {

    }

    public FeatureVersionModel(String version, String description) {
        this.version = version;
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
