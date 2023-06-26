package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.models.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.*;
import org.reactivestreams.Publisher;
import io.reactivex.Flowable;

import java.util.ArrayList;
import java.util.List;
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

    @Post(uri="add", consumes= MediaType.MULTIPART_FORM_DATA)
    public RestRepository makeCommit(@PathVariable int repositoryHandlerId,
                                     Publisher<CompletedFileUpload> file,
                                     @RequestAttribute("message") String message,
                                     @RequestAttribute("config") String config,
                                    @RequestAttribute("username") String username){

        List<CompletedFileUpload> fileList = new ArrayList<>();
        Flowable.fromPublisher(file).subscribe(fileList::add);

        LOGGER.info(fileList.size() + " files uploaded.");

        return repositoryService.addCommit(repositoryHandlerId, message, config, username, fileList);
    }
}
