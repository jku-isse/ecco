package at.orsystems.smartmirror.weather.owm.forecast;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Represents the feels like JSON object from openweathermap for a weather forecast day.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"day", "night", "evening", "morning"})
public class TemperatureFeelsLike {
    /**
     * The forecast temperature during the day. Comes in whatever unit which is specified via the properties.
     */
    public final double day;
    /**
     * The forecast temperature during the night. Comes in whatever unit which is specified via the properties.
     */
    public final double night;
    /**
     * The forecast temperature during the evening. Comes in whatever unit which is specified via the properties.
     */
    public final double evening;
    /**
     * The forecast temperature during the morning. Comes in whatever unit which is specified via the properties.
     */
    public final double morning;
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public TemperatureFeelsLike(@JsonProperty(value = "day", required = true) double day,
                                @JsonProperty(value = "night", required = true) double night,
                                @JsonProperty(value = "evening", required = true) @JsonAlias("eve") double evening,
                                @JsonProperty(value = "morning", required = true) @JsonAlias("morn") double morning) {
        this.day = day;
        this.night = night;
        this.evening = evening;
        this.morning = morning;
    }

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
        return new ToStringBuilder(this).append("day", day)
                .append("night", night)
                .append("eve", evening)
                .append("morning", morning)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(additionalProperties)
                .append(day)
                .append(evening)
                .append(morning)
                .append(night)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof TemperatureFeelsLike rhs)) {
            return false;
        }
        return new EqualsBuilder().append(additionalProperties, rhs.additionalProperties)
                .append(day, rhs.day)
                .append(evening, rhs.evening)
                .append(morning, rhs.morning)
                .append(night, rhs.night)
                .isEquals();
    }
}
