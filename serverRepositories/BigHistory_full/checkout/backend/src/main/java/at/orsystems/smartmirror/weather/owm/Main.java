package at.orsystems.smartmirror.weather.owm;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Represents the main JSON object from openweathermap.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"temp", "feels_like", "temp_min", "temp_max", "pressure", "humidity"})
public class Main {
    /**
     * The current temperature. Comes in whatever unit which is specified via the properties.
     */
    public final double temperature;
    /**
     * This temperature accounts for the human perception of weather. Comes in whatever unit which is specified via
     * the properties.
     */
    public final double feelsLikeTemperature;
    /**
     * Minimum temperature at the moment. This is the minimal currently observed temperature. Comes in whatever unit
     * which is specified via the properties.
     */
    public final double temperatureMin;
    /**
     * Maximum temperature at the moment. This is the maximal currently observed temperature. Comes in whatever unit
     * which is specified via the properties.
     */
    public final double temperatureMax;
    /**
     * Atmospheric pressure on the sea level in hPa
     */
    public final double pressure;
    /**
     * The humidity in %.
     */
    public final double humidity;
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public Main(@JsonProperty(value = "temperature", required = true) @JsonAlias("temp") double temperature,
                @JsonProperty(value = "feelsLikeTemperature", required = true) @JsonAlias("feels_like") double feelsLikeTemperature,
                @JsonProperty(value = "temperatureMin", required = true) @JsonAlias("temp_min") double temperatureMin,
                @JsonProperty(value = "temperatureMax", required = true) @JsonAlias("temp_max") double temperatureMax,
                @JsonProperty(value = "pressure", required = true) double pressure,
                @JsonProperty(value = "humidity", required = true) double humidity) {
        this.temperature = temperature;
        this.feelsLikeTemperature = feelsLikeTemperature;
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.pressure = pressure;
        this.humidity = humidity;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        requireNonNull(name);
        requireNonNull(value);
        this.additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("temp", temperature)
                .append("feelsLike", feelsLikeTemperature)
                .append("tempMin", temperatureMin)
                .append("tempMax", temperatureMax)
                .append("pressure", pressure)
                .append("humidity", humidity)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(feelsLikeTemperature)
                .append(temperatureMax)
                .append(temperature)
                .append(humidity)
                .append(pressure)
                .append(additionalProperties)
                .append(temperatureMin)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Main rhs)) {
            return false;
        }
        return new EqualsBuilder().append(feelsLikeTemperature, rhs.feelsLikeTemperature)
                .append(temperatureMax, rhs.temperatureMax)
                .append(temperature, rhs.temperature)
                .append(humidity, rhs.humidity)
                .append(pressure, rhs.pressure)
                .append(additionalProperties, rhs.additionalProperties)
                .append(temperatureMin, rhs.temperatureMin)
                .isEquals();
    }

}
