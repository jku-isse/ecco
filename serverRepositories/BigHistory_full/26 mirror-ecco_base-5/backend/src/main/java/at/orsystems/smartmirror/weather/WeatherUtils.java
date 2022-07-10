package at.orsystems.smartmirror.weather;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Contains utility methods for generating the URLs for fetching the weather data.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@Component
final class WeatherUtils {
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/%s/weather?q=%s&units=%s&appid=%s&lang=%s";
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/%s/forecast/daily?q=%s&units=%s&appid=%s&lang=%s&cnt=%d";

    @NonNull
    private final String openWeatherMapVersion;
    @NonNull
    private final String openWeatherMapUnits;
    @NonNull
    private final String openWeatherMapAPIKey;
    @NonNull
    private final String openWeatherMapLanguage;
    @NonNull
    private final int openWeatherMapForecastDays;

    public WeatherUtils(@Value("${openweathermap.version}") @NonNull final String openWeatherMapVersion,
                        @Value("${openweathermap.units}") @NonNull final String openWeatherMapUnits,
                        @Value("${openweathermap.API}") @NonNull final String openWeatherMapAPIKey,
                        @Value("${smartmirror.language}") @NonNull final String openWeatherMapLanguage,
                        @Value("${openweathermap.forecastDays}") final int openWeatherMapForecastDays) {
        this.openWeatherMapVersion = requireNonNull(openWeatherMapVersion);
        this.openWeatherMapUnits = requireNonNull(openWeatherMapUnits);
        this.openWeatherMapAPIKey = requireNonNull(openWeatherMapAPIKey);
        this.openWeatherMapLanguage = requireNonNull(openWeatherMapLanguage);
        this.openWeatherMapForecastDays = openWeatherMapForecastDays;
    }

    /**
     * Returns the URL for fetching the current weather data from openweathermap for the given city name.
     *
     * @param cityName
     *         the name of the city
     */
    @NonNull
    public String getCurrentWeatherUrl(@NonNull String cityName) {
        requireNonNull(cityName);
        return format(WEATHER_URL,
                openWeatherMapVersion,
                cityName,
                openWeatherMapUnits,
                openWeatherMapAPIKey,
                openWeatherMapLanguage);
    }

    /**
     * Returns the URL for fetching the weather forecast data from openweathermap for the given city name.
     *
     * @param cityName
     *         the name of the city
     */
    @NonNull
    public String getForecastUrl(@NonNull String cityName) {
        requireNonNull(cityName);
        return format(FORECAST_URL,
                openWeatherMapVersion,
                cityName,
                openWeatherMapUnits,
                openWeatherMapAPIKey,
                openWeatherMapLanguage,
                openWeatherMapForecastDays);
    }
}
