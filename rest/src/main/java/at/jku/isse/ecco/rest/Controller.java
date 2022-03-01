package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;


@RestController
@RequestMapping("api")
public class Controller {
    private EccoService service = new EccoService();
/*    @Autowired
    private EccoService service;*/


    @PostMapping("test")
    public void setRoadAvailable() {
        System.out.println("test");
    }

    @GetMapping("repository")
    public Repository getRepository () {
        //TODO select Repro
        Path baseDir = Path.of(System.getProperty("user.dir"), "examples\\image_variants");
        System.out.println(baseDir);
        service.setRepositoryDir(baseDir);
        service.setBaseDir(baseDir.getParent());





        return service.getRepository();
    }

}
