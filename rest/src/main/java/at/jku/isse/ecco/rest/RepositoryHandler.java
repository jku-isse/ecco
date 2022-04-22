package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.rest.classes.RestRepository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.feature.MemConfiguration;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RepositoryHandler {

    private final int rId;
    private final String name;
    private final Path path;
    private EccoService service;

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public RepositoryHandler(Path path, int rId) {
        name = path.getFileName().toString();
        this.rId = rId;
        this.path = path;
    }

    public boolean isInitialized () {
        return service != null;
    }

    public RestRepository getRepository() {
        if(!isInitialized()) {
            service = new EccoService();
            service.setRepositoryDir(path.resolve(".ecco"));
            service.setBaseDir(path);
            service.open();
        }
        return new RestRepository(service, rId, name);
    }

    public RestRepository createRepository() {
        service = new EccoService();
        service.setRepositoryDir(path.resolve(".ecco"));
        service.setBaseDir(path);
        service.init();
        service.open();
        return new RestRepository(service, rId, name);
    }

    // Commit ----------------------------------------------------------------------------------------------------------
    public RestRepository addCommit (String message, String config, Path commitFolder, String committer) {
        service.setBaseDir(commitFolder);
        service.commit(message, config, committer);
        return getRepository();
    }

    // Variant ---------------------------------------------------------------------------------------------------------
    public RestRepository addVariant(String name, String config, String description){
        // TODO use config
        Configuration configuration = new MemConfiguration(new FeatureRevision[0]);
        service.addVariant(configuration, name, description, service);
        return getRepository();
    }

    public RestRepository removeVariant(String variantId){
        service.getRepository().removeVariant(service.getRepository().getVariant(variantId));
        return getRepository();
    }

    public RestRepository variantSetNameDescription(String variantId, String name, String description) {
        Variant variant = service.getRepository().getVariant(variantId);
        variant.setName(name);
        variant.setDescription(description);
        return getRepository();
    }

    public RestRepository variantAddFeature(String variantId, String featureId){

        List<FeatureRevision> list = new LinkedList<>(Arrays.stream(service.getRepository().getVariant(variantId).getConfiguration().getFeatureRevisions()).toList());

        for(Feature f : service.getRepository().getFeature()){
            if (f.getId().equals(featureId)) {
                list.add(f.getLatestRevision());
            }
        }

        service.getRepository().getVariant(variantId).getConfiguration().setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        return getRepository();
    }

    public RestRepository variantUpdateFeature(String variantId, String featureName, String id){
        System.out.println("Update FeatureRevision " + featureName + " from variant " + variantId + " to Revision " + id);

        FeatureRevision[] featureRevisions =  service.getRepository().getVariant(variantId).getConfiguration().getFeatureRevisions();
        for (int i = 0; i < featureRevisions.length ; i++) {
            if (featureRevisions[i].getFeature().getName().equals(featureName)){
                Feature f =  service.getRepository().getFeature().stream().filter(fe -> fe.getName().equals(featureName)).findAny().orElse(null);
                featureRevisions[i] = f.getRevision(id);
                break;
            }
        }
        return getRepository();
    }

    public RestRepository variantRemoveFeature(String variantId, String featureName) {
        FeatureRevision[] arr = service.getRepository().getVariant(variantId).getConfiguration().getFeatureRevisions();
        List<FeatureRevision> list = new LinkedList<>();

        for (FeatureRevision rev : arr) {
            if (!rev.getFeature().getName().equals(featureName))
                list.add(rev);
        }

        service.getRepository().getVariant(variantId).getConfiguration().setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        return getRepository();
    }

    // Feature ---------------------------------------------------------------------------------------------------------
    public RestRepository setFeatureDescription(String featureId, String description) {
        service.getRepository().getFeatures().stream().filter(x -> x.getId().equals(featureId)).findAny().ifPresent(x -> x.setDescription(description));
        return getRepository();
    }

    public RestRepository setFeatureRevisionDescription(String featureId, String revisionId, String description) {
        service.getRepository().getFeatures().stream().filter(x -> x.getId().equals(featureId)).findAny().get().getRevision(revisionId).setDescription(description);
        return getRepository();
    }


}
