package at.orsystems.smartmirror.traffic;

import at.orsystems.smartmirror.traffic.pojo.TrafficItems;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@RestController
public class TrafficController {
    private final TrafficService trafficService;

    public TrafficController(TrafficService trafficService) {
        this.trafficService = trafficService;
    }

    @GetMapping("/traffic")
    public TrafficItems traffic(@RequestParam(value = "name", defaultValue = "Default") String name) {
        try {
            return trafficService.getTrafficItems();
        } catch (Exception e) {
            System.out.println("error....");
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, e.getMessage(), e);
        }
    }
}
