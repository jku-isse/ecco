package at.orsystems.smartmirror.weather.dto;

import at.orsystems.smartmirror.common.units.UnitSystem;
import at.orsystems.smartmirror.weather.owm.CurrentWeather;
import at.orsystems.smartmirror.weather.owm.WeatherDetails;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * This class is used to define the object that gets transferred to the frontend when an current weather request gets
 * performed.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
public class CurrentWeatherDTO {
    public final String groupDescription;
    public final String detailDescription;
    public final String iconPath;
    public final String temperature;
    public final String temperatureMax;
    public final String temperatureMin;
    public final String temperatureFeelsLike;
    public final String humidity;
    public final String windSpeed;
    public final double windDirection;
    public final String cloudiness;
    public final long requestDate;
    public final String countryCode;
    public final long sunrise;
    public final long sunset;
    public final long timezone;
    public final long cityId;
    public final String cityName;

    @JsonIgnore
    private final UnitSystem unitSystem;

    public CurrentWeatherDTO(final CurrentWeather currentWeather, final UnitSystem unitSystem) {
        requireNonNull(currentWeather);
        this.unitSystem = requireNonNull(unitSystem);
        /* TODO: what if this is empty and what if there are more? */
        final WeatherDetails details = currentWeather.weatherDetails.get(0);
        groupDescription = details.weatherSummary;
        detailDescription = details.weatherDescription;
        iconPath = details.iconPath;
        temperature = format(currentWeather.mainInformation.temperature, unitSystem::temperatureUnit);
        temperatureMax = format(currentWeather.mainInformation.temperatureMax, unitSystem::temperatureUnit);
        temperatureMin = format(currentWeather.mainInformation.temperatureMin, unitSystem::temperatureUnit);
        temperatureFeelsLike = format(currentWeather.mainInformation.feelsLikeTemperature, unitSystem::temperatureUnit);
        humidity = format(currentWeather.mainInformation.humidity, unitSystem::percentageUnit);
        windSpeed = format(currentWeather.wind.speed, unitSystem::speedUnit);
        windDirection = currentWeather.wind.degrees;
        cloudiness = format(currentWeather.clouds.cloudiness, unitSystem::percentageUnit);
        requestDate = currentWeather.requestDate;
        countryCode = currentWeather.system.countryId;
        sunrise = currentWeather.system.sunrise;
        sunset = currentWeather.system.sunset;
        timezone = currentWeather.timezone;
        cityId = currentWeather.cityId;
        cityName = currentWeather.cityName;
    }

    private static String format(double number, Supplier<String> unitSupplier) {
        return String.format("%.1f%s", number, unitSupplier.get());
    }
}
