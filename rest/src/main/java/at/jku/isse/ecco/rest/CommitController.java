package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;

@Controller("/api/{rId}/commit")
public class CommitController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();

    @Post(uri="add", consumes= MediaType.MULTIPART_FORM_DATA)
    public RestRepository makeCommit(@PathVariable int rId,
                                     @RequestAttribute("file") CompletedFileUpload[] uploadingFiles,
                                     @RequestAttribute("message") String message,
                                     @RequestAttribute("config") String config) {

        //TODO change hardcoded Committer to username of frontend
        return repositoryService.addCommit(rId, message, config, "Online Committer", uploadingFiles);
    }

}
