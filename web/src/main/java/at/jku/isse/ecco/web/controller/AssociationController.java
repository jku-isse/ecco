package at.jku.isse.ecco.web.controller;

import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;

@Path("/associations")
public class AssociationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public AssociationModel[] getAssociations() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        AssociationRepository associationRepository = (AssociationRepository) featureRepositoryContextResolver.getContext(AssociationRepository.class);
        return associationRepository.getAssociations();
    }

}
