package at.orsystems.smartmirror.weather;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static java.util.Objects.requireNonNull;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@Component
final class WeatherUtils {
    private static final String WEATHER_URL = "api.openweathermap.org/data/%s/weather?q=%s&units=%s&appid=%s";

    private static String openWeatherMapAPIKey;
    private static String openWeatherMapVersion;
    private static String openWeatherMapUnits;

    @Value("${openWeatherMap.version}")
    private String tOpenWeatherMapVersion;
    @Value("${openWeatherMap.units}")
    private String tOpenWeatherMapUnits;
    @Value("${openWeatherMap.API}")
    private String tOpenWeatherMapAPIKey;

    private WeatherUtils() {
    }

    public static String getCurrentWeatherUrl(@NonNull String cityName) {
        requireNonNull(cityName);
        return String.format(WEATHER_URL, openWeatherMapVersion, cityName, openWeatherMapUnits, openWeatherMapAPIKey);
    }

    public static String getForecastUrl(@NonNull String cityName) {
        return "";
    }

    @PostConstruct
    public void init() {
        WeatherUtils.openWeatherMapAPIKey = tOpenWeatherMapAPIKey;
        WeatherUtils.openWeatherMapUnits = tOpenWeatherMapUnits;
        WeatherUtils.openWeatherMapVersion = tOpenWeatherMapVersion;
    }
}
