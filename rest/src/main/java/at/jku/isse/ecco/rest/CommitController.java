package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.client.multipart.MultipartBody;

import java.nio.file.Path;

@Controller("/api/{rId}/commit")
public class CommitController {
    private RepositoryService repositoryService = RepositoryService.getInstance();

/*    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public RestRepository RestRepository (@PathVariable int rId, @Body MultipartBody body) {
        MultipartBody test = body;
        System.out.println(test);

        return repositoryService.getRepository(rId);
    }*/

    @Post("add")
    public RestRepository makeCommit(@PathVariable int rId, @RequestAttribute("file") MultipartBody[] uploadingFiles, @RequestAttribute("message") String message, @RequestAttribute("config") String config) {
        System.out.println("New Commit:");
        System.out.println("message: " + message +  ", config: " + config);

        // copy files to futureBaseDir festplatte

        // TODO Ordnerstruktur ohne zwischenspeichern?

        //service.setBaseDir();
        //service.commit("message", "configString");

        Path commitFolder = repositoryService.getRepoStorage().resolve("commit");

        /*// remove existing files
        try {
            FileSystemUtils.deleteRecursively(commitFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(MultipartFile uploadedFile : uploadingFiles) {

            File file = commitFolder.resolve(Path.of(uploadedFile.getOriginalFilename().substring(1))).toFile();

            File folder = file.getParentFile(); // create folders if they don't exist
            if(!folder.exists()){
                folder.mkdirs();
            }

            try (OutputStream os = new FileOutputStream(file)) {
                os.write(uploadedFile.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // TODO
        // service.setBaseDir(repositoryService.getService(5).getBaseDir());
        // service.commit(message, config);
*/
        return repositoryService.getRepository(rId);
    }


}