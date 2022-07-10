package at.orsystems.smartmirror.weather.owm.forecast;

import at.orsystems.smartmirror.weather.owm.WeatherDetails;
import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an entire openweathermap weather forecast day.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "dt",
        "sunrise",
        "sunset",
        "temp",
        "feels_like",
        "pressure",
        "humidity",
        "weather",
        "speed",
        "deg",
        "clouds",
        "pop",
        "rain",
        "snow"
})
public class ForecastDay {
    /**
     * Contains the date for the forecast, unix, UTC
     */
    public final long forecastDate;
    /**
     * The sunrise time, unix, UTC
     */
    public final long sunrise;
    /**
     * The sunset time, unix, UTC
     */
    public final long sunset;
    /**
     * The forecast Temperature JSON object for this forecast day.
     */
    public final Temperature temperature;
    /**
     * The forecast feels like temperature JSON object for this forecast day.
     */
    public final TemperatureFeelsLike temperatureFeelsLike;
    /**
     * Atmospheric pressure on the sea level in hPa
     */
    public final double pressure;
    /**
     * The humidity in %.
     */
    public final double humidity;
    /**
     * The WeatherDetails JSON Object for that forecast day. Should only have a single entry. Has to be a {@link List}
     * because that's the way the openweathermap API defined it.
     */
    public final List<WeatherDetails> weather;
    /**
     * Wind speed. Comes in whatever unit which is specified via the properties.
     */
    public final double speed;
    /**
     * The wind direction in degrees (meteorological).
     */
    public final double degrees;
    /**
     * The cloudiness in %.
     */
    public final double cloudiness;
    /**
     * The probability of perception. The class is used because it is optional. Hence, if it is not present, the value
     * will be {@code null} which results in not including it when serializing this object.
     */
    public final Double pop;
    /**
     * The precipitation volume, mm. The class is used because it is optional. Hence, if it is not present, the value
     * will be {@code null} which results in not including it when serializing this object.
     */
    public final Double rainAmount;
    /**
     * The snow volume, mm. The class is used because it is optional. Hence, if it is not present, the value will be
     * {@code null} which results in not including it when serializing this object.
     */
    public final Double snowAmount;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public ForecastDay(@JsonProperty(value = "forecastDate", required = true) @JsonAlias("dt") long forecastDate,
                       @JsonProperty(value = "sunrise", required = true) long sunrise,
                       @JsonProperty(value = "sunset", required = true) long sunset,
                       @JsonProperty(value = "temperature", required = true) @JsonAlias("temp") Temperature temperature,
                       @JsonProperty(value = "temperatureFeelsLike", required = true) @JsonAlias("feels_like") TemperatureFeelsLike temperatureFeelsLike,
                       @JsonProperty(value = "pressure", required = true) long pressure,
                       @JsonProperty(value = "humidity", required = true) long humidity,
                       @JsonProperty(value = "weather", required = true) List<WeatherDetails> weather,
                       @JsonProperty(value = "speed", required = true) double windSpeed,
                       @JsonProperty(value = "deg", required = true) @JsonAlias("degrees") long degrees,
                       @JsonProperty(value = "clouds", required = true) @JsonAlias("cloudiness") double cloudiness,
                       @JsonProperty(value = "pop") Double pop,
                       @JsonProperty(value = "rain") Double rainAmount,
                       @JsonProperty(value = "snow") Double snowAmount) {
        this.forecastDate = forecastDate;
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.temperature = temperature;
        this.temperatureFeelsLike = temperatureFeelsLike;
        this.pressure = pressure;
        this.humidity = humidity;
        this.weather = weather;
        this.speed = windSpeed;
        this.degrees = degrees;
        this.cloudiness = cloudiness;
        this.pop = pop;
        this.rainAmount = rainAmount;
        this.snowAmount = snowAmount;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("forecastDate", forecastDate)
                .append("sunrise", sunrise)
                .append("sunset", sunset)
                .append("temperature", temperature)
                .append("feelsLike", temperatureFeelsLike)
                .append("pressure", pressure)
                .append("humidity", humidity)
                .append("weather", weather)
                .append("speed", speed)
                .append("degrees", degrees)
                .append("cloudiness", cloudiness)
                .append("pop", pop)
                .append("rainAmount", rainAmount)
                .append("snowAmount", snowAmount)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(rainAmount)
                .append(snowAmount)
                .append(sunrise)
                .append(temperature)
                .append(degrees)
                .append(pressure)
                .append(cloudiness)
                .append(speed)
                .append(forecastDate)
                .append(temperatureFeelsLike)
                .append(pop)
                .append(sunset)
                .append(weather)
                .append(humidity)
                .append(additionalProperties)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof ForecastDay rhs)) {
            return false;
        }
        return new EqualsBuilder().append(rainAmount, rhs.rainAmount)
                .append(snowAmount, rhs.snowAmount)
                .append(sunrise, rhs.sunrise)
                .append(temperature, rhs.temperature)
                .append(degrees, rhs.degrees)
                .append(pressure, rhs.pressure)
                .append(cloudiness, rhs.cloudiness)
                .append(speed, rhs.speed)
                .append(forecastDate, rhs.forecastDate)
                .append(temperatureFeelsLike, rhs.temperatureFeelsLike)
                .append(pop, rhs.pop)
                .append(sunset, rhs.sunset)
                .append(weather, rhs.weather)
                .append(humidity, rhs.humidity)
                .append(additionalProperties, rhs.additionalProperties)
                .isEquals();
    }
}
