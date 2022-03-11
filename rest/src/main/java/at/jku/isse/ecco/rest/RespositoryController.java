package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.rest.classes.RestRepository;
import at.jku.isse.ecco.service.EccoService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.io.RecursiveDeleteOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.google.common.io.MoreFiles.deleteDirectoryContents;


@RestController
@RequestMapping("api/repository")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RespositoryController {
    private static final Logger LOGGER = Logger.getLogger(EccoService.class.getName());
    //private EccoService service = new EccoService();
    private Map<Integer, String> allRepros = null;


    @Autowired
    private EccoService service;
    private String all;

    @PostMapping("test")
    public void setRoadAvailable() {
        System.out.println("test");
    }


    //----------------------- Repository ----------------//
    @PostMapping("")
    public ResponseEntity getRepository () {
        Repository repository = service.getRepository();
        return new ResponseEntity(repository, HttpStatus.OK);
    }

    @PostMapping("all")
    public Map<Integer, String> getAllRep() {
        if (allRepros == null) {
            //TOdo Reporos
            allRepros = new HashMap<>();
            allRepros.put(1, "Repro1");
            allRepros.put(2, "Repro2");
            allRepros.put(3, "Repro3");
            allRepros.put(4, "Repro4");
            allRepros.put(5, "Repro5");
            allRepros.put(6, "Repro6");
        }
        return allRepros;
    }

    @PostMapping("create")
    public Map<Integer, String> create(String name) {
        //openRepository()  //TODO
        allRepros.put(allRepros.size() +1, name);
        return getAllRep();
    }

    @PostMapping("clone")
    public Map<Integer, String> create(Integer fromId, String name) {
        //openRepository()  //TODO
        //cloneRepository()
        allRepros.put(allRepros.size() +1, name);
        return getAllRep();
    }

    @PostMapping("open")
    public Repository openRepository () {
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
            return service.getRepository();
        }
        return null;        //TODO delete
    }

    @PostMapping("init")
    public ResponseEntity initRepository () {
        //TODO select Repro

        Path basePath = Path.of(System.getProperty("user.dir"), "examples\\image_variants");

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

        RestRepository rep = new RestRepository(service);

        return new ResponseEntity(rep, HttpStatus.OK);
    }

    @PostMapping("initBig")
    public RestRepository initBigRepository () {
        //TODO select Repro

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
        for(int commitNumber = 1; commitNumber < 3; commitNumber++) {
            service.setBaseDir(basePath.resolve(files[commitNumber].getName()));
            System.out.println(files[commitNumber].getName());
            service.commit(commitNumber + ". Commit: " + files[commitNumber].getName());
            time.add(System.currentTimeMillis()- prevTime);
            prevTime = System.currentTimeMillis();
        }

        RestRepository rep = new RestRepository(service);
        return rep;
    }

    //----------------------- Feature ----------------//

    @PostMapping("setFeatureDescription")
    public Repository setFeatureDescription(String featureId, String description) {
        service.getRepository().getFeature().stream().map(x -> {
            if(x.getId().equals(featureId)) {
                x.setDescription(description);
            }
            return x;
        });

        return service.getRepository();
    }

    @PostMapping("setFeatureRevisionDescription")
    public Repository setFeatureRevisionDescription(String featureId, String RevisionId, String description) {
        //service.getRepository(). ...
        return service.getRepository();
    }

    //----------------------- Commit ----------------//

    @PostMapping("commit")
    public Repository commit() {
        return service.getRepository();
    }





            @PostMapping("close")
    public void closeRepository () {
        service.close();
    }

}
