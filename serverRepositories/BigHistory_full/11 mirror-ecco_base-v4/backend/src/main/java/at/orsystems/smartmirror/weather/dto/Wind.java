package at.orsystems.smartmirror.weather.dto;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"speed", "deg"})
public class Wind {
    public final double speed;
    public final double degrees;
    /* TODO: include direction only to be serialized...
     * see: https://www.baeldung.com/jackson-field-serializable-deserializable-or-not
     */
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

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
        if ((other instanceof Wind) == false) {
            return false;
        }
        Wind rhs = ((Wind) other);
        return new EqualsBuilder().append(additionalProperties, rhs.additionalProperties)
                                  .append(speed, rhs.speed)
                                  .append(degrees, rhs.degrees)
                                  .isEquals();
    }
}
