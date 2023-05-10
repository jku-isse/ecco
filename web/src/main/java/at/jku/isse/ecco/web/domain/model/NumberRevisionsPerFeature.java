package at.jku.isse.ecco.web.domain.model;

public class NumberRevisionsPerFeature {
    private int numberRevisions;
    private String featureName;

    public NumberRevisionsPerFeature(int numberRevisions, String featureName) {
        this.numberRevisions = numberRevisions;
        this.featureName = featureName;
    }

    public NumberRevisionsPerFeature() {

    }


    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public int getNumberRevisions() {
        return numberRevisions;
    }

    public void setNumberRevisions(int numberRevisions) {
        this.numberRevisions = numberRevisions;
    }
}
