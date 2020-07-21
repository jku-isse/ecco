package at.jku.isse.ecco.web.controller;


import at.jku.isse.ecco.web.domain.model.ArtefactGraphModel;
import at.jku.isse.ecco.web.domain.model.ArtefactTreeModel;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.ArtefactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;

@Path("/artefacts")
public class ArtefactController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtefactController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public ArtefactTreeModel getArtifactsByAssociation(AssociationModel[] givenAssociations) {
        ContextResolver<AbstractRepository> artefactRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        ArtefactRepository artefactRepository = (ArtefactRepository) artefactRepositoryContextResolver.getContext(ArtefactRepository.class);
        return artefactRepository.getArtifactsByAssociation(givenAssociations);
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public ArtefactGraphModel getCompleteArtefactgraph() {
        ContextResolver<AbstractRepository> artefactRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        ArtefactRepository artefactRepository = (ArtefactRepository) artefactRepositoryContextResolver.getContext(ArtefactRepository.class);
        return artefactRepository.getArtefactgraphFromAllAssociations();
    }
}
