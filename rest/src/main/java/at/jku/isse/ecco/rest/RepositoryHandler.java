package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.rest.models.RestRepository;
import at.jku.isse.ecco.service.EccoService;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Holds {@see EccoService} with one Repository
 * EccoService gets initialized at first usage of the Repository.
 * Each Repository has its own EccoService for Multi user support and performance (loading Repository takes some time)
 */
public class RepositoryHandler {
    private final int repositoryHandlerId;
    private final String name;
    private final Path path;
    // opened lazily by service(); volatile + its own lock so that opening never needs this handler's
    // monitor (fork() touches a second handler while holding its own - see service())
    private volatile EccoService eccoService;
    private final Object openLock = new Object();

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public RepositoryHandler(Path path, int repositoryHandlerId) {
        name = path.getFileName().toString();
        this.repositoryHandlerId = repositoryHandlerId;
        this.path = path;
    }

    public boolean isInitialized() {
        return eccoService != null;
    }

    /**
     * The repository's service, opened on first use. Handlers found by FileRepositoryService's
     * directory scan used to stay unopened until something called getRepository(), so e.g. a
     * commit to one failed with a NullPointerException.
     */
    private EccoService service() {
        EccoService service = this.eccoService;
        if (service == null) {
            synchronized (this.openLock) {
                service = this.eccoService;
                if (service == null) {
                    service = new EccoService();
                    service.setRepositoryDir(path.resolve(".ecco"));
                    service.setBaseDir(path);
                    service.open();
                    this.eccoService = service;
                }
            }
        }
        return service;
    }

    public synchronized RestRepository getRepository() {
        return new RestRepository(service(), repositoryHandlerId, name);
    }

    public synchronized void createRepository() {
        synchronized (this.openLock) {
            EccoService service = new EccoService();
            service.setRepositoryDir(path.resolve(".ecco"));
            service.setBaseDir(path);
            service.init();
            this.eccoService = service;
        }
    }

    // Commit ----------------------------------------------------------------------------------------------------------
    public synchronized void addCommit(String message, String config, Path commitFolder, String committer) {
        service().setBaseDir(commitFolder);
        service().commit(message, config, committer);
    }

    //checkout
    public synchronized void checkout(String variantId, Path checkoutPath) {
        service().setBaseDir(checkoutPath);
        service().checkout(service().getRepository().getVariant(variantId).getConfiguration());
    }

    // Variant ---------------------------------------------------------------------------------------------------------
    public synchronized RestRepository addVariant(String name, String config, String description) {
        service().addVariant(config, name, description);
        return getRepository();
    }

    public synchronized RestRepository removeVariant(String variantId) {
        service().removeVariant(variantId);
        return getRepository();
    }

    public synchronized RestRepository variantSetNameDescription(String variantId, String name, String description) {
        Variant variant = service().getRepository().getVariant(variantId);
        variant.setName(name);
        variant.setDescription(description);
        service().store();
        return getRepository();
    }

    public synchronized RestRepository variantAddFeature(String variantId, String featureId) {

        List<FeatureRevision> list = new LinkedList<>(Arrays.stream(service()
                        .getRepository()
                        .getVariant(variantId)
                        .getConfiguration()
                        .getFeatureRevisions())
                .toList());

        for (Feature f : service().getRepository().getFeature()) {
            if (f.getId().equals(featureId)) {
                list.add(f.getLatestRevision());
            }
        }

        service().getRepository()
                .getVariant(variantId)
                .getConfiguration()
                .setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        service().store();
        return getRepository();
    }

    public synchronized RestRepository variantUpdateFeature(String variantId, String featureName, String id) {
        System.out.println("Update FeatureRevision " + featureName + " from variant " + variantId + " to Revision " + id);

        FeatureRevision[] featureRevisions = service()
                .getRepository()
                .getVariant(variantId)
                .getConfiguration()
                .getFeatureRevisions();
        for (int i = 0; i < featureRevisions.length; i++) {
            if (featureRevisions[i].getFeature().getName().equals(featureName)) {
                Feature f = service()
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
        service().store();
        return getRepository();
    }

    public synchronized RestRepository variantRemoveFeature(String variantId, String featureName) {
        FeatureRevision[] arr = service()
                .getRepository()
                .getVariant(variantId)
                .getConfiguration()
                .getFeatureRevisions();
        List<FeatureRevision> list = new LinkedList<>();

        for (FeatureRevision rev : arr) {
            if (!rev.getFeature().getName().equals(featureName))
                list.add(rev);
        }

        service().getRepository()
                .getVariant(variantId)
                .getConfiguration()
                .setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        service().store();
        return getRepository();
    }

    // Feature ---------------------------------------------------------------------------------------------------------
    public synchronized RestRepository setFeatureDescription(String featureId, String description) {
        service().getRepository()
                .getFeatures()
                .stream()
                .filter(x -> x.getId().equals(featureId))
                .findAny().ifPresent(x -> x.setDescription(description));
        service().store();
        return getRepository();
    }

    public synchronized RestRepository setFeatureRevisionDescription(String featureId, String revisionId, String description) {
        service()
                .getRepository()
                .getFeatures()
                .stream()
                .filter(x -> x.getId().equals(featureId))
                .findAny()
                .get()
                .getRevision(revisionId).setDescription(description);
        service().store();
        return getRepository();
    }

    public synchronized void fork(RepositoryHandler origRepo, final String disabledFeatures) {
        service().forkAlreadyOpen(origRepo.service(), disabledFeatures);
    }
}
