package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.feature.Feature;

import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.FeatureModel;
import at.jku.isse.ecco.web.domain.model.FeatureVersionModel;
import at.jku.isse.ecco.web.rest.EccoApplication;

import java.util.ArrayList;
import java.util.Collection;

public class FeatureRepository extends AbstractRepository {

    private EccoApplication application;

    public FeatureRepository() {

    }

    public FeatureRepository(EccoApplication application) {
        this.application = application;
    }


    public FeatureModel[] updateFeatures(FeatureModel[] featureModels) {
        for (Feature feature : this.application.getEccoService().getRepository().getFeatures()) {
            for (FeatureModel changedFeature : featureModels) {
                if (feature.getName().equals(changedFeature.getName())) {
                    if (!feature.getDescription().equals(changedFeature.getDescription())) {
                        feature.setDescription(changedFeature.getDescription());
                    }
                }
            }
        }

        ArrayList<FeatureModel> features = new ArrayList<>();
        for (Feature feature : this.application.getEccoService().getRepository().getFeatures()) {
            features.add(new FeatureModel(feature.getName(), feature.getDescription()));
            LOGGER.info("FEATURE DESCRIPTION:" + feature.getDescription());
        }
        return features.toArray(new FeatureModel[0]);
    }

    /**
     *
     * @return FeatureModel[]
     */
    public FeatureModel[] getFeatures() {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Feature> featureCollection = eccoService.getRepository().getFeatures();
        ArrayList<FeatureModel> features = new ArrayList<>();
        for (Feature feature : featureCollection) {
            features.add(new FeatureModel(feature.getName(), feature.getDescription()));
        }
        return features.toArray(new FeatureModel[0]);
    }

    /**
     *
     * @param featureName String
     * @return FeatureModel
     */
    public FeatureModel getFeature(String featureName) {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Feature> featureCollection = eccoService.getRepository().getFeatures();
        for (Feature feature : featureCollection) {
            if (feature.getName().equals(featureName)) {
                return new FeatureModel(feature.getName(), feature.getDescription());
            }
        }
        return null;
    }

    /**
     *
     * @param featureName String
     * @return FeatureVersionModel[]
     */
    public FeatureVersionModel[] getFeatureVersionsFromFeature(String featureName) {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Feature> featureCollection = eccoService.getRepository().getFeatures();
        ArrayList<FeatureVersionModel> featureVersionModelsForOneFeature = new ArrayList<>();
        for (Feature feature : featureCollection) {
            if (feature.getName().equals(featureName)) {
                for (FeatureRevision featureRevision : feature.getRevisions()) {
                    featureVersionModelsForOneFeature.add(new FeatureVersionModel(featureRevision.getId(), featureRevision.getDescription()));
                }
            }
        }
        return featureVersionModelsForOneFeature.toArray(new FeatureVersionModel[0]);
    }

    public FeatureVersionModel getFeatureVersionFromFeatureAndFeatureVersion(String featureName, String featureVersion) {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Feature> featureCollection = eccoService.getRepository().getFeatures();
        for (Feature feature : featureCollection) {
            if (feature.getName().equals(featureName)) {
                for (FeatureRevision featureRevision : feature.getRevisions()) {
                    if (featureRevision.getId().equals(featureVersion)) {
                        return new FeatureVersionModel(featureRevision.getId(), featureRevision.getDescription());
                    }
                }
            }
        }
        return null;
    }
}
