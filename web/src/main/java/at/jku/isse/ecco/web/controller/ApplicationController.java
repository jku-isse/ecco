package at.jku.isse.ecco.web.controller;

import at.jku.isse.ecco.web.domain.model.ApplicationInitialization;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.ApplicationRepository;
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

@Path("/")
public class ApplicationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    @POST
    @Consumes( {MediaType.APPLICATION_JSON} )
    @Produces( {MediaType.APPLICATION_JSON} )
    public Response initializeRepository(ApplicationInitialization applicationContainer) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        ApplicationRepository applicationRepository = (ApplicationRepository) featureRepositoryContextResolver.getContext(ApplicationRepository.class);
        if (applicationRepository.initializeRepository(applicationContainer.getRepositoryDirectory())) {
            return Response.status(Response.Status.OK).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/corstest")
    @Produces( {MediaType.APPLICATION_JSON} )
    public Response init() {
        return Response.status(Response.Status.OK).build();
    }
}
