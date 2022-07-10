package at.orsystems.smartmirror.weather.dto;

import at.orsystems.smartmirror.common.units.UnitSystem;
import at.orsystems.smartmirror.weather.owm.forecast.WeatherForecast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * This class is used to define the object that gets transferred to the frontend when a weather forecast request gets
 * made.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
public class WeatherForecastDTO {
    public final String cityName;
    public final String country;
    public final long forecastDays;
    public final List<WeatherForecastDayDTO> weatherForecast;

    public WeatherForecastDTO(WeatherForecast forecast, UnitSystem unitSystem) {
        requireNonNull(forecast);
        requireNonNull(unitSystem);

        this.cityName = forecast.city.name;
        this.country = forecast.city.country;
        this.forecastDays = forecast.forecastDays;

        var forecastDayList = new ArrayList<WeatherForecastDayDTO>();

        for (final var forecastDay : forecast.forecastWeatherDays) {
            final var weatherDetails = forecastDay.weather.get(0);
            final var weatherForecastDay = new WeatherForecastDayDTO(weatherDetails.weatherDescription,
                    weatherDetails.iconPath,
                    format(forecastDay.temperature.min, unitSystem::temperatureUnit),
                    format(forecastDay.temperature.max, unitSystem::temperatureUnit),
                    forecastDay.forecastDate);
            forecastDayList.add(weatherForecastDay);
        }

        this.weatherForecast = unmodifiableList(forecastDayList);
    }

    private static String format(double number, Supplier<String> unitSupplier) {
        return String.format("%.1f%s", number, unitSupplier.get());
    }

    private static class WeatherForecastDayDTO {
        public final String detailDescription;
        public final String iconPath;
        public final String temperatureMin;
        public final String temperatureMax;
        public final long forecastDate;

        private WeatherForecastDayDTO(String detailDescription,
                                      String iconPath,
                                      String temperatureMin,
                                      String temperatureMax,
                                      long forecastDate) {
            this.detailDescription = detailDescription;
            this.iconPath = iconPath;
            this.temperatureMin = temperatureMin;
            this.temperatureMax = temperatureMax;
            this.forecastDate = forecastDate;
        }
    }
}
