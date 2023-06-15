package at.jku.isse.ecco.web.controller;

import at.jku.isse.ecco.web.domain.model.ArtifactsPerDepth;
import at.jku.isse.ecco.web.domain.model.AssociationArtifactsModel;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.domain.model.ModulesPerOrder;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

@Path("/associations")
public class AssociationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public AssociationModel[] getAssociations() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        AssociationRepository associationRepository = (AssociationRepository) featureRepositoryContextResolver.getContext(AssociationRepository.class);
        return associationRepository.getAssociations();
    }

    @GET
    @Path("/numberofartifacts")
    @Produces({MediaType.APPLICATION_JSON})
    public AssociationArtifactsModel[] getNumberOfArtifactsPerAssociation() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        AssociationRepository associationRepository = (AssociationRepository) featureRepositoryContextResolver.getContext(AssociationRepository.class);
        return associationRepository.getNumberOfArtifactsPerAssociation();
    }

    @GET
    @Path("/artifactsperdepth")
    @Produces({MediaType.APPLICATION_JSON})
    public ArtifactsPerDepth[] getArtifactsPerDepth() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        AssociationRepository associationRepository = (AssociationRepository) featureRepositoryContextResolver.getContext(AssociationRepository.class);
        return associationRepository.getArtifactsPerDepth();
    }

    @GET
    @Path("/modulesperorder")
    @Produces({ MediaType.APPLICATION_JSON })
    public ModulesPerOrder[] getModulesPerOrder() {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        AssociationRepository associationRepository = (AssociationRepository) featureRepositoryContextResolver.getContext(AssociationRepository.class);
        return associationRepository.getModulesPerOrder();
    }
}
