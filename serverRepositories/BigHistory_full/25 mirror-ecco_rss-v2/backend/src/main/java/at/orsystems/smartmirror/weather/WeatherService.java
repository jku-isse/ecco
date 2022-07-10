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

import javax.validation.constraints.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * This is the @{@link Service} that fetches the data from openweathermap.org and transforms it into our data
 * structures.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@Service
public class WeatherService {
    @NotNull
    private final RestTemplate restTemplate;
    @NotNull
    private final UnitSystem unitSystem;
    @NotNull
    private final WeatherUtils weatherUtils;

    public WeatherService(@NotNull final RestTemplateBuilder restTemplateBuilder,
                          @NotNull final UnitSystem unitSystem,
                          @NotNull final WeatherUtils weatherUtils) {
        this.restTemplate = requireNonNull(restTemplateBuilder).build();
        this.unitSystem = requireNonNull(unitSystem);
        this.weatherUtils = requireNonNull(weatherUtils);
    }

    /**
     * Fetches the current weather data from openweathermap for the given city.
     *
     * @param cityName
     *         the name of the city for which the current weather data shall be fetched.
     * @return a transferable object that contains all the weather information needed.
     * @throws RestClientException
     *         if fetching the weather data failed.
     */
    public CurrentWeatherDTO getCurrentWeather(final String cityName) throws RestClientException {
        final var currentWeather = restTemplate.getForObject(weatherUtils.getCurrentWeatherUrl(cityName), CurrentWeather.class);
        return new CurrentWeatherDTO(currentWeather, unitSystem);
    }

    /**
     * Fetches the weather forecast data from openweathermap for the given city.
     *
     * @param cityName
     *         the name of the city for which the current weather data shall be fetched.
     * @return a transferable object that contains all the weather forecast information needed.
     * @throws RestClientException
     *         if fetching the weather data failed.
     */
    public WeatherForecastDTO getWeatherForecast(final String cityName) throws RestClientException {
        final var forecast = restTemplate.getForObject(weatherUtils.getForecastUrl(cityName), WeatherForecast.class);
        return new WeatherForecastDTO(forecast, unitSystem);
    }
}
