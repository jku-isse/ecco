package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;

//@Component
public class RestRepository implements Serializable {
    private final int repositoryHandlerId;
    private final String name;
    private final EccoService service;

    public RestRepository(EccoService service, int repositoryHandlerId, String name) {
        this.repositoryHandlerId = repositoryHandlerId;
        this.name = name;
        this.service = service;
    }

    public int getRepositoryHandlerId() {
        return repositoryHandlerId;
    }

    public Collection<? extends RestFeature> getFeatures() {
        Collection<RestFeature> features = new LinkedList<>();
        for (Feature f : service.getRepository().getFeatures()) {
            features.add(new RestFeature(f));
        }
        return features;
    }

    public String getName() {
        return name;
    }

    public Collection<RestCommit> getCommits () {
        Collection<RestCommit> commits = new LinkedList<>();
        for (Commit c : service.getRepository().getCommits()) {
            commits.add(new RestCommit(c));
        }
        return commits;
    }

    public Collection<RestVariant> getVariants () {
        Collection<RestVariant> variants = new LinkedList<>();
        if (service.getRepository().getVariants() != null) {
            for (Variant v : service.getRepository().getVariants()) {
                variants.add(new RestVariant(v));
            }
        }
        return variants;
    }
}
