package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.models.RestRepository;
import io.micronaut.core.async.annotation.*;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.*;
import org.reactivestreams.*;
import reactor.core.publisher.*;

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
                                                Publisher<CompletedFileUpload> file,
                                                @RequestAttribute("message") String message,
                                                @RequestAttribute("config") String config,
                                                @RequestAttribute("username") String username) {
        return Flux.from(file)
                .collectList()
                .map(fileList -> repositoryService.addCommit(repositoryHandlerId, message, config, username, fileList));
    }
}
