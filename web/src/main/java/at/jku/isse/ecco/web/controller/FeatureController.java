package at.jku.isse.ecco.web.controller;


import at.jku.isse.ecco.web.domain.model.FeatureModel;
import at.jku.isse.ecco.web.domain.model.FeatureVersionModel;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.FeatureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;

@Path("features")
public class FeatureController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public FeatureModel[] updateFeature(FeatureModel featureModels) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        return featureRepository.updateFeature(featureModels);
    }

    /**
     *
     * @return String
     */
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
    public Response updateFeatureversionFromFeature(@PathParam("featureName") String featureName, FeatureVersionModel featureVersionModel) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        featureRepository.updateFeatureVersionFromFeature(featureName, featureVersionModel);
        return Response.status(Response.Status.OK).allow("POST, GET, OPTIONS, PUT, DELETE").build();
    }
}