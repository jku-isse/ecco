package at.orsystems.smartmirror.weather.owm;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Represents the Clouds JSON object form openweathermap.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"all"})
public class Clouds {
    /**
     * The cloudiness in %.
     */
    public final double cloudiness;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public Clouds(@JsonProperty(value = "cloudiness", required = true) @JsonAlias("all") double cloudiness) {
        this.cloudiness = cloudiness;
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
        return new ToStringBuilder(this).append("cloudiness", cloudiness)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(cloudiness)
                .append(additionalProperties)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Clouds rhs)) {
            return false;
        }
        return new EqualsBuilder().append(cloudiness, rhs.cloudiness)
                .append(additionalProperties, rhs.additionalProperties)
                .isEquals();
    }
}
