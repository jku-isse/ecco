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
    private RepositoryService repositoryService = RepositoryService.getInstance();

/*    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public RestRepository RestRepository (@PathVariable int rId, @Body MultipartBody body) {
        MultipartBody test = body;
        System.out.println(test);

        return repositoryService.getRepository(rId);
    }*/

    @Post(uri="add", consumes= MediaType.MULTIPART_FORM_DATA )
    public RestRepository makeCommit(@PathVariable int rId, @RequestAttribute("file") CompletedFileUpload[] uploadingFiles, @RequestAttribute("message") String message, @RequestAttribute("config") String config) {
        System.out.println("New Commit:");
        System.out.println("message: " + message +  ", config: " + config);

        return repositoryService.addCommit(rId, message, config, uploadingFiles);
    }



}
