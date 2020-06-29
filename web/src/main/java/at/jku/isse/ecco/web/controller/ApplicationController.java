package at.jku.isse.ecco.web.controller;

import at.jku.isse.ecco.web.domain.model.*;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;

@Path("/repository")
public class ApplicationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    /**
     * Create new Repo with a given
     * @param operation
     * @return
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createRepository(OperationContainer operation) {
        return Response.status(Response.Status.OK).build();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public OperationResponse doOpenCloseRepository(OperationContainer operationOnDirectory) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        ApplicationRepository applicationRepository = (ApplicationRepository) featureRepositoryContextResolver.getContext(ApplicationRepository.class);
        return applicationRepository.doOpenCloseOperationOnRepository(
                operationOnDirectory.getRepositoryDirectory(),
                operationOnDirectory.getRepositoryOperation());
    }

    @POST
    @Path("/corstest")
    @Produces( {MediaType.APPLICATION_JSON} )
    public FeatureModel[] init() {
        return new FeatureModel[]{
                new FeatureModel("dpl1", "Das ist ein geile Beschreibung!!!!!"),
                new FeatureModel("dpl2", "Das ist ein eher nicht so geile Beschreibung!!!!!")
        };
    }
}
