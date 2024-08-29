package at.jku.isse.ecco.experiment.utils;

public class FeatureUtils {

    public static boolean featureNameIsValid(String featureName){
        return featureName.matches("((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('|(\\.([a-zA-Z0-9_-])+))?");
    }

}
