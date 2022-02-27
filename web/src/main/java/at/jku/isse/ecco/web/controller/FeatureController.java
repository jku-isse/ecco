package at.jku.isse.ecco.web.controller;


import at.jku.isse.ecco.web.domain.model.FeatureModel;
import at.jku.isse.ecco.web.domain.model.FeatureVersionModel;
import at.jku.isse.ecco.web.domain.model.NumberRevisionsPerFeature;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.FeatureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;

@Path("features")
public class FeatureController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    @GET
    @Path("/test")
    @Produces({ MediaType.APPLICATION_JSON })
    public FeatureModel[] getTestFeature() {
        System.out.println("Testing");
        FeatureModel[] arr = new FeatureModel[2];
        arr[0]= new FeatureModel("person", "persondesc" );
        arr[1]= new FeatureModel("shirt", "shirtdesc" );
        return arr;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public FeatureModel[] updateFeature(FeatureModel featureModels) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        return featureRepository.updateFeature(featureModels);
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public FeatureModel[] getFeatures() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        return featureRepository.getFeatures();
    }

    @GET
    @Path("/{featureName}/version")
    @Produces({ MediaType.APPLICATION_JSON })
    public FeatureVersionModel[] getFeatureversionsFromFeature(@PathParam("featureName") String featureName) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        return featureRepository.getFeatureVersionsFromFeature(featureName);
    }

    @POST
    @Path("/{featureName}/version")
    @Produces({ MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_JSON })
    public FeatureVersionModel[] updateFeatureversionFromFeature(@PathParam("featureName") String featureName, FeatureVersionModel featureVersionModel) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        return featureRepository.updateFeatureVersionFromFeature(featureName, featureVersionModel);
    }

    @GET
    @Path("/numberofrevisions")
    @Produces({ MediaType.APPLICATION_JSON })
    public NumberRevisionsPerFeature[] getNumberRevisionsPerFeature() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        return featureRepository.getNumberRevisionsPerFeature();
    }
}
