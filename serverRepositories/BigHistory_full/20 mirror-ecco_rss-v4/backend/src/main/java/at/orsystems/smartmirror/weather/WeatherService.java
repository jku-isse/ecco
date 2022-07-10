package at.orsystems.smartmirror.weather;

import at.orsystems.smartmirror.common.units.UnitSystem;
import at.orsystems.smartmirror.weather.dto.CurrentWeatherDTO;
import at.orsystems.smartmirror.weather.dto.WeatherForecastDTO;
import at.orsystems.smartmirror.weather.owm.CurrentWeather;
import at.orsystems.smartmirror.weather.owm.forecast.WeatherForecast;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static at.orsystems.smartmirror.weather.WeatherUtils.getCurrentWeatherUrl;
import static at.orsystems.smartmirror.weather.WeatherUtils.getForecastUrl;

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
        final var currentWeather = restTemplate.getForObject(getCurrentWeatherUrl(cityName), CurrentWeather.class);
        return new CurrentWeatherDTO(currentWeather, unitSystem);
    }

    public WeatherForecastDTO getWeatherForecast(final String cityName) throws RestClientException {
        System.err.println(getForecastUrl(cityName));
        final var forecast = restTemplate.getForObject(getForecastUrl(cityName), WeatherForecast.class);
        return new WeatherForecastDTO(forecast, unitSystem);
    }
}
