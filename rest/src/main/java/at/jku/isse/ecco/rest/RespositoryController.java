package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.module.MemModule;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


@RestController
@RequestMapping("api/repository")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RespositoryController {
    private static final Logger LOGGER = Logger.getLogger(EccoService.class.getName());


    @Autowired
    private EccoService service;

    @PostMapping("test")
    public void setRoadAvailable() {
        System.out.println("test");
    }

    @GetMapping("")
    public ResponseEntity getRepository () {
        Repository repository = service.getRepository();
        Collection features = repository.getFeatures();
        Collection<Association.Op> associations;
        ArrayList<Variant> variants;
        List<Map<MemModule, MemModule>> modules;
        Collection<Commit> commits;


        return new ResponseEntity(repository, HttpStatus.OK);
    }


    @GetMapping("open")
    public ResponseEntity openRepository () {
        //TODO select Repro
        if (service.isInitialized()) {
            LOGGER.info("Repository already in use");
            return new ResponseEntity("The Repository is currently used by an other user", HttpStatus.FORBIDDEN);
        } else {
            Path baseDir = Path.of(System.getProperty("user.dir"), "examples\\image_variants");
            System.out.println(baseDir);
            service.setRepositoryDir(baseDir);
            service.setBaseDir(baseDir.getParent());
            service.open();
            return new ResponseEntity(service.getRepository(), HttpStatus.OK);
        }
    }

}
