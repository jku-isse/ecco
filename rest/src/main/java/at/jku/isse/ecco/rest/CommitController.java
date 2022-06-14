package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import org.reactivestreams.Publisher;
import io.reactivex.Flowable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api/{rId}/commit")
public class CommitController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();
    private static final Logger LOGGER = Logger.getLogger(RepositoryService.class.getName());

    @Post(uri="add", consumes= MediaType.MULTIPART_FORM_DATA)
    public RestRepository makeCommit(@PathVariable int rId,
                                     Publisher<CompletedFileUpload> file,
                                     @RequestAttribute("message") String message,
                                     @RequestAttribute("config") String config,
                                    @RequestAttribute("username") String username){

        List<CompletedFileUpload> fileList = new ArrayList<>();
        Flowable.fromPublisher(file).subscribe(fileList::add);

        LOGGER.info(fileList.size() + " files uploaded.");

        return repositoryService.addCommit(rId, message, config, username, fileList);
    }

}
