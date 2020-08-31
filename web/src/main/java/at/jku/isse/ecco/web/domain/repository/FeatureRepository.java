package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.feature.Feature;

import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.ArtefactGraphModel;
import at.jku.isse.ecco.web.domain.model.FeatureModel;
import at.jku.isse.ecco.web.domain.model.FeatureVersionModel;
import at.jku.isse.ecco.web.domain.model.NumberRevisionsPerFeature;
import at.jku.isse.ecco.web.rest.EccoApplication;

import java.util.ArrayList;
import java.util.Collection;

public class FeatureRepository extends AbstractRepository {

    private EccoApplication application;

    public FeatureRepository() {}

    public FeatureRepository(EccoApplication application) {
        this.application = application;
    }


    public FeatureModel[] updateFeature(FeatureModel featureModels) {
        for (Feature feature : this.application.getEccoService().getRepository().getFeatures()) {
            if (feature.getName().equals(featureModels.getName())) {
                if (!feature.getDescription().equals(featureModels.getDescription())) {
                    feature.setDescription(featureModels.getDescription());
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
        ArtefactGraphModel backendGraph = this.application.getBackendGraph();
        if (backendGraph == null) {
            LOGGER.info("Backendgraph aus Application null...");
        } else {
            LOGGER.info("Backendgraph aus Application nicht null...!!!");
        }
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

    public FeatureVersionModel[] updateFeatureVersionFromFeature(String featureName, FeatureVersionModel featureVersionModel) {
        //Update der Features solange das Repo nicht geschlossen wird...
        for (Feature feature : this.application.getEccoService().getRepository().getFeatures()) {
            if (feature.getName().equals(featureName)) {
                for (FeatureRevision featureRevision : feature.getRevisions()) {
                    if (featureRevision.getId().equals(featureVersionModel.getVersion())) {
                        featureRevision.setDescription(featureVersionModel.getDescription());
                    }
                }
            }
        }
        //Neues abschicken der Liste mit geupdateten Beschreibungen
        ArrayList<FeatureVersionModel> featureVersionModelsForOneFeature = new ArrayList<>();
        for (Feature feature : this.application.getEccoService().getRepository().getFeatures()) {
            if (feature.getName().equals(featureName)) {
                for (FeatureRevision featureRevision : feature.getRevisions()) {
                    featureVersionModelsForOneFeature.add(new FeatureVersionModel(featureRevision.getId(), featureRevision.getDescription()));
                }
            }
        }
        return featureVersionModelsForOneFeature.toArray(new FeatureVersionModel[0]);
    }

    public NumberRevisionsPerFeature[] getNumberRevisionsPerFeature() {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Feature> featureCollection = eccoService.getRepository().getFeatures();
        ArrayList<NumberRevisionsPerFeature> listOfNumberOfRevivionsPerFeature = new ArrayList<>();
        for (Feature feature : featureCollection) {
            int numRevisions = feature.getRevisions().size();
            if (numRevisions > 0) {
                listOfNumberOfRevivionsPerFeature.add(new NumberRevisionsPerFeature(numRevisions, feature.getName()));
            }
        }
        return listOfNumberOfRevivionsPerFeature.toArray(new NumberRevisionsPerFeature[0]);
    }
}
