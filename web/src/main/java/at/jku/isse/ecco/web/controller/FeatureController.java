package at.jku.isse.ecco.web.controller;


import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.web.domain.model.FeatureModel;
import at.jku.isse.ecco.web.domain.model.FeatureVersionModel;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.FeatureRepository;
import at.jku.isse.ecco.web.rest.EccoApplication;
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

    /***
     *
     * @return String
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getFeatures() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        FeatureModel[] featureModels = featureRepository.getFeatures();
        return Response.status(Response.Status.OK).entity(featureModels).build();
    }

    @GET
    @Path("/{featureName}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getFeature(@PathParam("featureName") String featureName) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        FeatureModel searchedFeatureModel = featureRepository.getFeature(featureName);
        return Response.status(Response.Status.OK).entity(searchedFeatureModel).build();
    }

    @GET
    @Path("/{featureName}/version")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getFeatureversionsFromFeature(@PathParam("featureName") String featureName) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        FeatureVersionModel[] featureVersionModels = featureRepository.getFeatureVersionsFromFeature(featureName);
        return Response.status(Response.Status.OK).entity(featureVersionModels).build();
    }

    @GET
    @Path("/{featureName}/version/{featureversionName}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getFeatureversionFromFeatureAndVersionname(@PathParam("featureName") String featureName, @PathParam("featureversionName") String versionName) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        FeatureRepository featureRepository = (FeatureRepository) featureRepositoryContextResolver.getContext(FeatureRepository.class);
        FeatureVersionModel featureVersionModel = featureRepository.getFeatureVersionFromFeatureAndFeatureVersion(featureName, versionName);
        return Response.status(Response.Status.OK).entity(featureVersionModel).build();
    }
}