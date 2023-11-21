package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.models.RestRepository;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.logging.Logger;

@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api/{repositoryHandlerId}/commit")
public class CommitController {
    private final RepositoryService repositoryService;
    private static final Logger LOGGER = Logger.getLogger(CommitController.class.getName());

    @Inject
    public CommitController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Post(value = "add", consumes = MediaType.MULTIPART_FORM_DATA)
    @SingleResult
    public Publisher<RestRepository> makeCommit(@PathVariable int repositoryHandlerId,
                                                @Part("file") Publisher<CompletedFileUpload> file,
                                                @Part("message") String message,
                                                @Part("config") String config,
                                                @Part("username") String username) {
        return Flux.from(file)
                .collectList()
                .map(fileList -> repositoryService.addCommit(repositoryHandlerId, message, config, username, fileList));
    }
}
