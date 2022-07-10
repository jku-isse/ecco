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
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/%s/weather?q=%s&units=%s&appid=%s&lang=%s";
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/%s/forecast/daily?q=%s&units=%s&appid=%s&lang=%s&cnt=%d";

    private static String openWeatherMapAPIKey;
    private static String openWeatherMapVersion;
    private static String openWeatherMapUnits;
    private static String openWeatherMapLanguage;
    private static int openWeatherMapForecastDays;

    @Value("${openweathermap.version}")
    private String tOpenWeatherMapVersion;
    @Value("${openweathermap.units}")
    private String tOpenWeatherMapUnits;
    @Value("${openweathermap.API}")
    private String tOpenWeatherMapAPIKey;
    @Value("${smartmirror.language}")
    private String tOpenWeatherMapLanguage;
    @Value("${openweathermap.forecastDays}")
    private int tOpenWeatherMapForecastDays;

    private WeatherUtils() {
    }

    public static String getCurrentWeatherUrl(@NonNull String cityName) {
        requireNonNull(cityName);
        return String.format(WEATHER_URL,
                openWeatherMapVersion,
                cityName,
                openWeatherMapUnits,
                openWeatherMapAPIKey,
                openWeatherMapLanguage);
    }


    public static String getForecastUrl(@NonNull String cityName) {
        requireNonNull(cityName);
        return String.format(FORECAST_URL,
                openWeatherMapVersion,
                cityName,
                openWeatherMapUnits,
                openWeatherMapAPIKey,
                openWeatherMapLanguage,
                openWeatherMapForecastDays);
    }

    @PostConstruct
    public void init() {
        WeatherUtils.openWeatherMapAPIKey = tOpenWeatherMapAPIKey;
        WeatherUtils.openWeatherMapUnits = tOpenWeatherMapUnits;
        WeatherUtils.openWeatherMapVersion = tOpenWeatherMapVersion;
        WeatherUtils.openWeatherMapLanguage = tOpenWeatherMapLanguage;
        WeatherUtils.openWeatherMapForecastDays = tOpenWeatherMapForecastDays;
    }
}
