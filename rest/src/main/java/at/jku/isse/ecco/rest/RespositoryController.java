package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.rest.classes.RestFeature;
import at.jku.isse.ecco.rest.classes.RestRepository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.core.MemVariant;
import at.jku.isse.ecco.storage.mem.feature.MemConfiguration;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.io.RecursiveDeleteOption;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static com.google.common.io.MoreFiles.deleteDirectoryContents;


@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("api/repository")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RespositoryController {
    private static final Logger LOGGER = Logger.getLogger(EccoService.class.getName());
    private EccoService service = new EccoService();        //TODO delete

    private RepositoryService repositoryService = new RepositoryService();

    @PostMapping("test")
    public void test() {
        System.out.println("test");
    }


    //----------------------- Repository ----------------//
    @PostMapping("{id}")
    public ResponseEntity getRepository (@PathVariable int rId) {
        return new ResponseEntity(repositoryService.getRepository(rId), HttpStatus.OK);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping("all")
    public RepoHeader[] getAllRep() {
        return repositoryService.getAllRepositories().entrySet().stream().map(e -> new RepoHeader(e.getKey(), e.getValue())).toArray(RepoHeader[]::new);
    }

    @PutMapping("{name}")           //create Repository
    @ResponseStatus(HttpStatus.OK)
    public RepoHeader[] create(@PathVariable String name) {
        Path p = repositoryService.getRepoStorage().resolve(name);
        if (p.toFile().exists()) {
            throw new ResponseStatusException(HttpStatus.IM_USED, "Repository with this name already exists");
        }
        p.toFile().mkdir();     //create File
        service.setRepositoryDir(p.resolve(".ecco"));
        service.setBaseDir(p);
        service.init();
        return getAllRep();
    }

    @PostMapping("open/{id}")
    public RestRepository openRepository(@PathVariable int id) {
        getAllRep();

        Collection<RestFeature> featureList =  new LinkedList<>();
        Repository r = repositoryService.getRepository(id);
        Collection<? extends Feature> test = r.getFeatures();
        for (Feature f : test) {
            //TODO geht nicht einmal so obwohl die Rückgabe nichts mit dem EccoService zu tun hat.
/*            List<RestFeatureRevision> revisions = new ArrayList<>();
            for (FeatureRevision r : f.getRevisions()) {
                revisions.add(new RestFeatureRevision(r.getId(), r.getDescription(), r.getFeatureRevisionString()));
            }
            featureList.add(new RestFeature(f.getId(), f.getName(), revisions));*/
        }
        RestRepository rep = new RestRepository("test", featureList);
        return rep;
    }


    @PostMapping("clone")
    public RepoHeader[] create(Integer fromId, String name) {
        //openRepository()  //TODO
        //cloneRepository()

        return getAllRep();
    }

    @PostMapping("openTest")
    public RestRepository openTestRepository () {
        //TODO select Repro
        if (service.isInitialized()) {
            LOGGER.info("Repository already in use");
           // return new RestRepository("The Repository is currently used by an other user", HttpStatus.FORBIDDEN);
        } else {
            Path baseDir = Path.of(System.getProperty("user.dir"), "examples\\BigHistory\\.ecco");
            System.out.println(baseDir);
            service.setBaseDir(baseDir.getParent());
            service.setRepositoryDir(baseDir);
            service.open();

            //RestRepository rep = new RestRepository(service);

            Repository r = service.getRepository();

            //RestRepository rep = new RestRepository(service);
            return null;
        }
        return null;        //TODO delete
    }

    @PostMapping("initTest")
    public ResponseEntity initRepository () {
        //TODO select Repro

        Path basePath = Path.of(System.getProperty("user.dir"), "examples\\image_variantsTest");

        //create Repo
        String repo = ".ecco";
        Path p = basePath.resolve(repo);
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //ALLOW INSECURE
            Files.delete(p);        //Works only if the dir is already empty. (done by  deleteDirectoryContents)
        } catch (IOException e) {
            e.printStackTrace();
        }
        service.setRepositoryDir(p);
        service.init();

        int variantsCnt = 0;
        service.setBaseDir(basePath.resolve("V1_purpleshirt"));
        service.commit("V1");
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V2_stripedshirt"));
        service.commit("V2");
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V3_purpleshirt_jacket"));
        service.commit("V3");
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V4_purpleshirt_jacket_glasses"));
        service.commit("V4");
        variantsCnt++;

        System.out.printf("Committed %d variants\n", variantsCnt);

        //RestRepository rep = new RestRepository(service);

        return null; // new ResponseEntity(rep, HttpStatus.OK);
    }

    private static class RepoHeader implements Persistable {
        public int id;
        public String name;

        public RepoHeader(int id, String name){
            this.id=id;
            this.name=name;
        }

        public int getId(){return id;}
        public String getName() {return name;}


    }

    private static Repository currentrepo;

    @CrossOrigin(origins = "http://localhost:3000")
    @PostMapping("initBig")
    public Repository initBigRepository () {
        //TODO select Repro

        if (currentrepo != null ) return currentrepo;

        Path basePath = Path.of(System.getProperty("user.dir"), "examples\\bigHistory");
        basePath = basePath.getParent().resolve("BigHistory");

        //create Repo
        String repo = ".ecco";
        Path p = basePath.resolve(repo);
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //ALLOW INSECURE
            Files.delete(p);        //Works only if the dir is already empty. (done by  deleteDirectoryContents)
        } catch (IOException e) {
            e.printStackTrace();
        }

        service.setRepositoryDir(p);
        service.init();

        List<Long> time = new ArrayList<>();
        File[] files = basePath.toFile().listFiles();
        long prevTime = System.currentTimeMillis();
        for(int commitNumber = 1; commitNumber < 5; commitNumber++) {
            service.setBaseDir(basePath.resolve(files[commitNumber].getName()));
            System.out.println(files[commitNumber].getName());
            service.commit(commitNumber + ". Commit: " + files[commitNumber].getName());
            time.add(System.currentTimeMillis()- prevTime);
            prevTime = System.currentTimeMillis();
            System.out.println(commitNumber);
        }

        Repository r = service.getRepository();
        currentrepo = r;
        return r;
    }

    //----------------------- Feature ----------------//
    @PostMapping("/{featureId}/description")
    public Repository setFeatureDescription(@PathVariable String featureId, @RequestBody String description) {
        currentrepo.getFeatures().stream().filter(x -> x.getId().equals(featureId)).findAny().ifPresent(x -> x.setDescription(description));
        return currentrepo;
    }

    @PostMapping("/{featureId}/{revisionId}/description")
    public Repository setFeatureRevisionDescription(@PathVariable String featureId,  @PathVariable String revisionId, @RequestBody String description) {
        currentrepo.getFeatures().stream().filter(x -> x.getId().equals(featureId)).findAny().get().getRevision(revisionId).setDescription(description);
        return currentrepo;
    }

    //----------------------- Commit ----------------//

    @PostMapping("commit")
    public Repository commit() {
        return service.getRepository();
    }

    // TODO compare commits?

    //----------------------- Variant ----------------//
    @PutMapping("/variant/{name}")
    public Repository createVariant(@PathVariable String name, @RequestBody String config){
        // TODO create new Variant with config from string??
        Configuration configuration = new MemConfiguration(new FeatureRevision[0]);
        System.out.println("Creating variant with config: "+config);
        Variant var = new MemVariant(name, configuration, "new ID"); // TODO id > uuid

        currentrepo.addVariant(var);
        return currentrepo;
    }

    @DeleteMapping("/variant/{variantId}")
    public Repository deleteVariant(@PathVariable String variantId){
        System.out.println("DELETE Variant "+ variantId);
        currentrepo.removeVariant(currentrepo.getVariant(variantId));
        return currentrepo;
    }

    @PutMapping("/variant/{variantId}/feature/{featureId}")
    public Repository variantAddFeature(@PathVariable String variantId, @PathVariable String featureId){
        System.out.println("Add Feature " + featureId + " to variant " + variantId);

        List<FeatureRevision> list = new LinkedList<>();
        list.addAll(Arrays.stream(currentrepo.getVariant(variantId).getConfiguration().getFeatureRevisions()).toList());

        for(Feature f : currentrepo.getFeature()){
            if (f.getId().equals(featureId)) {
                list.add(f.getLatestRevision());
            }
        }

        currentrepo.getVariant(variantId).getConfiguration().setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        return currentrepo;
    }

    @PostMapping("/variant/{variantId}/feature/{featureName}")
    public Repository variantUpdateFeature(@PathVariable String variantId, @PathVariable String featureName, @RequestBody String id){
        System.out.println("Update FeatureRevision " + featureName + " from variant " + variantId + " to Revision " + id);

        FeatureRevision[] featureRevisions = currentrepo.getVariant(variantId).getConfiguration().getFeatureRevisions();
        for (int i = 0; i < featureRevisions.length ; i++) {
            if (featureRevisions[i].getFeature().getName().equals(featureName)){
                Feature f = currentrepo.getFeature().stream().filter(fe -> fe.getName().equals(featureName)).findAny().orElse(null);
                featureRevisions[i] = f.getRevision(id);
                break;
            }
        }
        return currentrepo;
    }

    @DeleteMapping("/variant/{variantId}/feature/{featureName}")
    public Repository variantRemoveFeature(@PathVariable String variantId, @PathVariable String featureName){
        System.out.println("Remove Feature " + featureName + " from variant " + variantId);
        FeatureRevision[] arr = currentrepo.getVariant(variantId).getConfiguration().getFeatureRevisions();
        List<FeatureRevision> list = new LinkedList<>();

        for ( FeatureRevision rev : arr){
            if (!rev.getFeature().getName().equals(featureName))
                list.add(rev);
        }

        currentrepo.getVariant(variantId).getConfiguration().setFeatureRevisions(list.toArray(new FeatureRevision[0]));
        return currentrepo;
    }






    @PostMapping("close")
    public void closeRepository () {
        service.close();
    }

}
