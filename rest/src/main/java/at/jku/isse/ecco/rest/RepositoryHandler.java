package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.rest.classes.RestRepository;
import at.jku.isse.ecco.service.EccoService;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/** Holds {@see EccoService} with one Repository
 * EccoService gets initialized at first usage of the Repository.
 * Each Repository has its own EccoService for Multi user support and performance (loading Repository takes some time)
 */
public class RepositoryHandler {

    private final int rId;
    private final String name;
    private final Path path;
    private EccoService eccoService;

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
        return eccoService != null;
    }

    public RestRepository getRepository() {
        if(!isInitialized()) {
            eccoService = new EccoService();
            eccoService.setRepositoryDir(path.resolve(".ecco"));
            eccoService.setBaseDir(path);
            eccoService.open();
        }
        return new RestRepository(eccoService, rId, name);
    }

    public void createRepository() {
        eccoService = new EccoService();
        eccoService.setRepositoryDir(path.resolve(".ecco"));
        eccoService.setBaseDir(path);
        eccoService.init();
        new RestRepository(eccoService, rId, name);
    }

    // Commit ----------------------------------------------------------------------------------------------------------
    public void addCommit (String message, String config, Path commitFolder, String committer) {
        eccoService.setBaseDir(commitFolder);
        eccoService.commit(message, config, committer);
    }

    //checkout
    public void checkout(String variantId, Path checkoutPath) {
        getRepository();
        eccoService.setBaseDir(checkoutPath);
        eccoService.checkout(eccoService.getRepository().getVariant(variantId).getConfiguration());
    }

    // Variant ---------------------------------------------------------------------------------------------------------
    public RestRepository addVariant(String name, String config, String description){
        eccoService.addVariant(config, name, description);
        return getRepository();
    }

    public RestRepository removeVariant(String variantId){
        eccoService.removeVariant(variantId);
        return getRepository();
    }

    public RestRepository variantSetNameDescription(String variantId, String name, String description) {
        Variant variant = eccoService.getRepository().getVariant(variantId);
        variant.setName(name);
        variant.setDescription(description);
        eccoService.store();
        return getRepository();
    }

    public RestRepository variantAddFeature(String variantId, String featureId){

        List<FeatureRevision> list = new LinkedList<>(Arrays.stream(eccoService
                                                                        .getRepository()
                                                                        .getVariant(variantId)
                                                                        .getConfiguration()
                                                                        .getFeatureRevisions())
                                                                        .toList());

        for(Feature f : eccoService.getRepository().getFeature()){
            if (f.getId().equals(featureId)) {
                list.add(f.getLatestRevision());
            }
        }

        eccoService.getRepository()
                    .getVariant(variantId)
                    .getConfiguration()
                    .setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        eccoService.store();
        return getRepository();
    }

    public RestRepository variantUpdateFeature(String variantId, String featureName, String id){
        System.out.println("Update FeatureRevision " + featureName + " from variant " + variantId + " to Revision " + id);

        FeatureRevision[] featureRevisions =  eccoService
                                                    .getRepository()
                                                    .getVariant(variantId)
                                                    .getConfiguration()
                                                    .getFeatureRevisions();
        for (int i = 0; i < featureRevisions.length ; i++) {
            if (featureRevisions[i].getFeature().getName().equals(featureName)){
                Feature f =  eccoService
                                    .getRepository()
                                    .getFeature()
                                    .stream()
                                    .filter(fe -> fe.getName().equals(featureName)).findAny().orElse(null);
                if (f != null) {
                    featureRevisions[i] = f.getRevision(id);
                }
                break;
            }
        }
        eccoService.store();
        return getRepository();
    }

    public RestRepository variantRemoveFeature(String variantId, String featureName) {
        FeatureRevision[] arr = eccoService
                                    .getRepository()
                                    .getVariant(variantId)
                                    .getConfiguration()
                                    .getFeatureRevisions();
        List<FeatureRevision> list = new LinkedList<>();

        for (FeatureRevision rev : arr) {
            if (!rev.getFeature().getName().equals(featureName))
                list.add(rev);
        }

        eccoService.getRepository()
                            .getVariant(variantId)
                            .getConfiguration()
                            .setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        eccoService.store();
        return getRepository();
    }

    // Feature ---------------------------------------------------------------------------------------------------------
    public RestRepository setFeatureDescription(String featureId, String description) {
        eccoService.getRepository()
                        .getFeatures()
                        .stream()
                        .filter(x -> x.getId().equals(featureId))
                        .findAny().ifPresent(x -> x.setDescription(description));
        eccoService.store();
        return getRepository();
    }

    public RestRepository setFeatureRevisionDescription(String featureId, String revisionId, String description) {
        eccoService
                .getRepository()
                .getFeatures()
                .stream()
                .filter(x -> x.getId().equals(featureId))
                .findAny()
                .get()
                .getRevision(revisionId).setDescription(description);
        eccoService.store();
        return getRepository();
    }

    public void fork(RepositoryHandler origRepo, final String disabledFeatures) {
        if(origRepo.eccoService == null) {
            origRepo.getRepository();
        }
        eccoService.forkAlreadyOpen(origRepo.eccoService, disabledFeatures);
    }
}
