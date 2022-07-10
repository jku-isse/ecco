package at.orsystems.smartmirror.weather.owm;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the wind JSON object from openweathermap.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"speed", "deg"})
public class Wind {
    /**
     * Wind speed. Comes in whatever unit which is specified via the properties.
     */
    public final double speed;
    /**
     * The wind direction in degrees (meteorological).
     */
    public final double degrees;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public Wind(@JsonProperty(value = "speed", required = true) double speed,
                @JsonProperty(value = "degrees", required = true) @JsonAlias("deg") double degrees) {
        this.speed = speed;
        this.degrees = degrees;
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
        return new ToStringBuilder(this).append("speed", speed)
                .append("degrees", degrees)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(additionalProperties)
                .append(speed)
                .append(degrees)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Wind)) {
            return false;
        }

        final var rhs = (Wind) other;

        return new EqualsBuilder().append(additionalProperties, rhs.additionalProperties)
                .append(speed, rhs.speed)
                .append(degrees, rhs.degrees)
                .isEquals();
    }
}
