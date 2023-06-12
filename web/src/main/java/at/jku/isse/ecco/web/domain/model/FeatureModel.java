package at.jku.isse.ecco.web.domain.model;

public class FeatureModel {

    public FeatureModel() {

    }

    public FeatureModel(String name, String description) {
        this.description = description;
        this.name = name;
    }

    private String name;
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
