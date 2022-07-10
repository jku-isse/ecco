package at.orsystems.smartmirror.common.dto;

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
@JsonPropertyOrder({"Longitude", "Latitude"})
public class CoordinateDTO {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final double longitude;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final double latitude;
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public CoordinateDTO(
            @JsonProperty(value = "longitude", required = true) @JsonAlias({"Longitude", "lng"}) double longitude,
            @JsonProperty(value = "latitude", required = true) @JsonAlias({"Latitude", "lat"}) double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
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
        return new ToStringBuilder(this).append("longitude", longitude)
                                        .append("latitude", latitude)
                                        .append("additionalProperties", additionalProperties)
                                        .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(additionalProperties)
                                    .append(longitude)
                                    .append(latitude)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof CoordinateDTO rhs)) {
            return false;
        }
        return new EqualsBuilder().append(additionalProperties, rhs.additionalProperties)
                                  .append(longitude, rhs.longitude)
                                  .append(latitude, rhs.latitude)
                                  .isEquals();
    }

}
