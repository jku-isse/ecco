package at.orsystems.smartmirror.weather.dto;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"temp", "feels_like", "temp_min", "temp_max", "pressure", "humidity"})
public class Main {
    public final double temperature;
    public final double feelsLikeTemperature;
    public final double temperatureMin;
    public final double temperatureMax;
    public final double pressure;
    public final double humidity;
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonCreator
    public Main(@JsonProperty(value = "temperature", required = true) @JsonAlias("temp") double temperature,
                @JsonProperty(value = "feelsLikeTemperature") @JsonAlias("feelsLike") double feelsLikeTemperature,
                @JsonProperty(value = "temperatureMin", required = true) @JsonAlias("tempMin") double temperatureMin,
                @JsonProperty(value = "temperatureMax", required = true) @JsonAlias("tempMax") double temperatureMax,
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
        if ((other instanceof Main) == false) {
            return false;
        }
        Main rhs = ((Main) other);
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
