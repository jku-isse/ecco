package at.orsystems.smartmirror.weather;

import at.orsystems.smartmirror.weather.dto.CurrentWeatherDTO;
import at.orsystems.smartmirror.weather.dto.WeatherForecastDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.constraints.NotNull;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * This is the controller that provides the endpoints for the current weather and the weather forecast.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@RestController
public class WeatherController {
    @NotNull
    private final WeatherService weatherService;

    public WeatherController(@NotNull final WeatherService weatherService) {
        this.weatherService = requireNonNull(weatherService);
    }

    @GetMapping("/weather")
    public CurrentWeatherDTO weather(@RequestParam("cityName") @NotNull final String cityName) {
        if (cityName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the given cityName is null!");
        }
        try {
            return weatherService.getCurrentWeather(cityName);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    format("Could not fetch weather data for %s because of %s.", cityName, ex.getMessage()),
                    ex
            );
        }
    }

    @GetMapping("/weatherForecast")
    public WeatherForecastDTO weatherForecast(@RequestParam("cityName") @NotNull final String cityName) {
        if (cityName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the given cityName is null!");
        }
        try {
            return weatherService.getWeatherForecast(cityName);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    format("Could not fetch weather forecast data for %s because of %s.", cityName, ex.getMessage()),
                    ex
            );
        }
    }
}
