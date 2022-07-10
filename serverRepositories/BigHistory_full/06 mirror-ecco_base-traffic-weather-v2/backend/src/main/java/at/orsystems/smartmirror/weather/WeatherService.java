package at.orsystems.smartmirror.weather;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@Service
public class WeatherService {
    private static final String WEATHER_URL = "";
    private final RestTemplate restTemplate;

    public WeatherService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

}
