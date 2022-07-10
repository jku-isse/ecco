package at.orsystems.smartmirror.weather.owm.forecast;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the temperature JSON object from openweathermap for a weather forecast day.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"day", "min", "max", "night", "evening", "morning"})
public class Temperature {
    /**
     * The forecast temperature during the day. Comes in whatever unit which is specified via the properties.
     * Is optional, hence the wrapper type because it won't get serialized.
     */
    public final Double day;
    /**
     * The minimum forecast temperature for that day. Comes in whatever unit which is specified via the properties.
     */
    public final double min;
    /**
     * The maximum forecast temperature for that day. Comes in whatever unit which is specified via the properties.
     */
    public final double max;
    /**
     * The forecast temperature during the night. Comes in whatever unit which is specified via the properties.
     */
    public final Double night;
    /**
     * The forecast temperature during the evening. Comes in whatever unit which is specified via the properties.
     * Is optional, hence the wrapper type because it won't get serialized.
     */
    public final Double evening;
    /**
     * The forecast temperature during the morning. Comes in whatever unit which is specified via the properties.
     * Is optional, hence the wrapper type because it won't get serialized.
     */
    public final Double morning;
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public Temperature(@JsonProperty(value = "day") Double day,
                       @JsonProperty(value = "min", required = true) double min,
                       @JsonProperty(value = "max", required = true) double max,
                       @JsonProperty(value = "night") Double night,
                       @JsonProperty(value = "evening") @JsonAlias("eve") Double evening,
                       @JsonProperty(value = "morning") @JsonAlias("morn") Double morning) {
        this.day = day;
        this.night = night;
        this.min = min;
        this.max = max;
        this.evening = evening;
        this.morning = morning;
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
        return new ToStringBuilder(this).append("day", day)
                .append("min", min)
                .append("max", max)
                .append("night", night)
                .append("evening", evening)
                .append("morning", morning)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(min)
                .append(max)
                .append(evening)
                .append(night)
                .append(additionalProperties)
                .append(day)
                .append(morning)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Temperature)) {
            return false;
        }

        final var rhs = (Temperature) other;

        return new EqualsBuilder().append(min, rhs.min)
                .append(max, rhs.max)
                .append(evening, rhs.evening)
                .append(night, rhs.night)
                .append(additionalProperties, rhs.additionalProperties)
                .append(day, rhs.day)
                .append(morning, rhs.morning)
                .isEquals();
    }
}