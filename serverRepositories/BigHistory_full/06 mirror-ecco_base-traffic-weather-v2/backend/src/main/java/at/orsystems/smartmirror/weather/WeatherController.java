package at.orsystems.smartmirror.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@RestController
public class WeatherController {
    private static final Logger logger = LoggerFactory.getLogger(WeatherController.class);
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Value("${openWeatherMap.API}")
    private String key;

    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping("/weather")
    public String weather() {
        return """
                {
                    "path":"./icons/wi-day-sunny.svg"
                }""";
        //return "./resources/icons/wi-day.sunny.svg";
    }
}
