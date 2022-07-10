package at.orsystems.smartmirror.weather;

import at.orsystems.smartmirror.common.units.UnitSystem;
import at.orsystems.smartmirror.startup.OpenWeatherMapProperties;
import at.orsystems.smartmirror.weather.dto.CurrentWeatherDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@RestController
public class WeatherController {
    private static final Logger logger = LoggerFactory.getLogger(WeatherController.class);
    private final UnitSystem unitSystem;
    private final WeatherService weatherService;
    private final OpenWeatherMapProperties properties;

    public WeatherController(WeatherService weatherService, OpenWeatherMapProperties properties, UnitSystem unitSystem) {
        this.weatherService = weatherService;
        this.properties = properties;
        this.unitSystem = unitSystem;
    }

    /*TODO: make this somehow configurable...*/
    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping("/weather")
    public CurrentWeatherDTO weather(@RequestParam("cityName") final String cityName) {
        return weatherService.getCurrentWeather(cityName);
    }
}
