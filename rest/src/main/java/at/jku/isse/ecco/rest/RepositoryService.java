package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.models.*;
import io.micronaut.http.multipart.*;

import java.nio.file.*;
import java.util.*;

public interface RepositoryService {
    // Repositories ----------------------------------------------------------------------------------------------------
    RestRepository getRepository(int repositoryHandlerId);

    RepositoryHandler createRepository(String name);

    void forkRepository(int oldRepositoryHandlerId, String name, String disabledFeatures);

    void cloneRepository(int oldRepositoryHandlerId, String name);

    void deleteRepository(int repositoryHandlerId);

    Map<Integer, RepositoryHandler> getRepositories();

    // Commit ----------------------------------------------------------------------------------------------------------
    RestRepository addCommit(int repositoryHandlerId, String message, String config, String committer, List<CompletedFileUpload> commitFiles);

    // Variant ---------------------------------------------------------------------------------------------------------
    RestRepository addVariant(int repositoryHandlerId, String name, String config, String description);

    RestRepository removeVariant(int repositoryHandlerId, String variantId);

    RestRepository variantSetNameDescription(int repositoryHandlerId, String variantId, String name, String description);

    RestRepository variantAddFeature(int repositoryHandlerId, String variantId, String featureId);

    RestRepository variantUpdateFeature(int repositoryHandlerId, String variantId, String featureName, String id);

    RestRepository variantRemoveFeature(int repositoryHandlerId, String variantId, String featureName);

    Path checkout(int repositoryHandlerId, String variantId);

    // Feature ---------------------------------------------------------------------------------------------------------
    RestRepository setFeatureDescription(int repositoryHandlerId, String featureId, String description);

    RestRepository setFeatureRevisionDescription(int repositoryHandlerId, String featureId, String revisionId, String description);

    void pullFeaturesRepository(int toRepositoryHandlerId, int oldRepositoryHandlerId, String deselectedFeatures);
}
