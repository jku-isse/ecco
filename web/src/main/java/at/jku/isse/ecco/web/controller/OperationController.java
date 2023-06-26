package at.jku.isse.ecco.web.controller;

import at.jku.isse.ecco.web.domain.model.CloseOperationResponse;
import at.jku.isse.ecco.web.domain.model.FeatureModel;
import at.jku.isse.ecco.web.domain.model.OperationContainer;
import at.jku.isse.ecco.web.domain.model.OperationResponse;
import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.domain.repository.OperationRepository;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.io.InputStream;

@Path("/repository")
public class OperationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationController.class);

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    /**
     * Create new Repo with a given
     * @param operationOnDirectory
     * @return
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public OperationResponse createRepository(OperationContainer operationOnDirectory) {
        //TODO: Create eines Repos einmal hierein packen...

        return new CloseOperationResponse();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public OperationResponse doOpenCloseRepository(OperationContainer operationOnDirectory) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        OperationRepository operationRepository = (OperationRepository) featureRepositoryContextResolver.getContext(OperationRepository.class);
        return operationRepository.doOpenCloseCreateOperationOnRepository(
                operationOnDirectory.getBaseDirectory(),
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
    @POST
    @Path("/commit")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response commitFilesInsideArchive(
            @FormDataParam("file") InputStream uploadedFileStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail
    ) {
        ContextResolver<AbstractRepository> featureRepositoryContextResolver = providers.getContextResolver(AbstractRepository.class, MediaType.WILDCARD_TYPE);
        OperationRepository operationRepository = (OperationRepository) featureRepositoryContextResolver.getContext(OperationRepository.class);
        String savedPathOfZIPFile = operationRepository.saveZIPFileOnPath(uploadedFileStream, fileDetail);
        operationRepository.commitFilesInsideSavedRepositoryOnPath(savedPathOfZIPFile, fileDetail.getFileName());
        return Response.ok().build();
    }

    @POST
    @Path("/checkout")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response checkoutFilesWithZIPArchive(

    ) {
        return Response.ok().build();
    }
}
