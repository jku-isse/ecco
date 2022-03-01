package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.service.EccoService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;


@RestController
@RequestMapping("api/Controller")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Controller {
    //private EccoService service = new EccoService();
    private static final Logger LOGGER = Logger.getLogger(EccoService.class.getName());


    @PostMapping("test")
    public void setRoadAvailable() {
        System.out.println("test");
    }

}
