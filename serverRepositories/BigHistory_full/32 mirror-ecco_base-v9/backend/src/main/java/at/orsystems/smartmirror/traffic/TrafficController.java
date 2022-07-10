package at.orsystems.smartmirror.traffic;

import at.orsystems.smartmirror.traffic.dto.TrafficItemsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * This @{@link RestController} defines the endpoints for the traffic module.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@RestController
public class TrafficController {
    private static final Logger logger = LoggerFactory.getLogger(TrafficController.class);
    private final TrafficService trafficService;

    public TrafficController(TrafficService trafficService) {
        this.trafficService = trafficService;
    }

    @GetMapping("/traffic")
    public TrafficItemsDTO traffic(@RequestParam(value = "name", defaultValue = "Default") String name) {
        try {
            return trafficService.getTrafficItems();

        } catch (RestClientException e) {
            logger.error("Could not retrieve traffic items from oe3 because of: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }
}
