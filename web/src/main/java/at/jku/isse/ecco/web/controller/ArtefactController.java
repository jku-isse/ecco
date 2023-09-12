package at.jku.isse.ecco.web.controller;


import at.jku.isse.ecco.web.domain.model.ArtefactGraphModel;
import at.jku.isse.ecco.web.domain.model.ArtefactTreeModel;
import at.jku.isse.ecco.web.domain.model.Artefactgraph.ArtefactgraphFilter;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.ArtefactRepository;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    @POST
    @Path("/graph")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public ArtefactGraphModel getCompleteArtefactgraph(ArtefactgraphFilter artefactgraphFilter) {
        ContextResolver<AbstractRepository> artefactRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        ArtefactRepository artefactRepository = (ArtefactRepository) artefactRepositoryContextResolver.getContext(ArtefactRepository.class);
        return artefactRepository.getArtefactgraphFromAllAssociations(artefactgraphFilter.getMaxChildCount());
    }

    @POST
    @Path("/updatedgraph")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public ArtefactGraphModel updateFrontendGraphByClickedNodeWithNodeID(ArtefactgraphFilter maxChildCountAndClickedNode) {
        ContextResolver<AbstractRepository> artefactRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        ArtefactRepository artefactRepository = (ArtefactRepository) artefactRepositoryContextResolver.getContext(ArtefactRepository.class);
        return artefactRepository.getUpdatedFrontendGraphByNodeID(maxChildCountAndClickedNode.getNodeID(), maxChildCountAndClickedNode.getMaxChildCount());
    }
}
