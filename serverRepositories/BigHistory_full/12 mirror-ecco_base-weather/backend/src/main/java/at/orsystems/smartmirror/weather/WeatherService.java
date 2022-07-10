package at.orsystems.smartmirror.weather;

import at.orsystems.smartmirror.common.units.UnitSystem;
import at.orsystems.smartmirror.weather.dto.CurrentWeatherDTO;
import at.orsystems.smartmirror.weather.owm.CurrentWeather;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@Service
public class WeatherService {
    private final RestTemplate restTemplate;
    private final UnitSystem unitSystem;

    public WeatherService(RestTemplateBuilder restTemplateBuilder, UnitSystem unitSystem) {
        this.restTemplate = restTemplateBuilder.build();
        this.unitSystem = unitSystem;
    }

    public CurrentWeatherDTO getCurrentWeather(final String cityName) throws RestClientException {
        final var currentWeather = restTemplate.getForObject(WeatherUtils.getCurrentWeatherUrl(cityName), CurrentWeather.class);
        return new CurrentWeatherDTO(currentWeather, unitSystem);
    }

}
