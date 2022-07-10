package at.orsystems.smartmirror.traffic;

import at.orsystems.smartmirror.traffic.dto.TrafficItemsDTO;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Defines the @{@link Service} for providing the actual traffic items from oe3.orf.at.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@Service
public class TrafficService {
    private static final String TRAFFIC_URL = "https://oe3meta.orf.at/oe3api/ApiV2.php/TrafficInfo.json";
    private final RestTemplate restTemplate;

    public TrafficService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * Gets the current Traffic Items from oe3.orf.at.
     *
     * @throws RestClientException
     *         if calling the url fails.
     */
    public TrafficItemsDTO getTrafficItems() throws RestClientException {
        return restTemplate.getForObject(TRAFFIC_URL, TrafficItemsDTO.class);
    }
}
