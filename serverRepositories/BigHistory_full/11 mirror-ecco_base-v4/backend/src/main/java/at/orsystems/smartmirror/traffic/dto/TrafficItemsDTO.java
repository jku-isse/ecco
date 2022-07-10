package at.orsystems.smartmirror.traffic.dto;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * These schemas are geneterated using http://www.jsonschema2pojo.org/.
 * Just copy any JSON string and it gives you the corresponding jackson or gson schema.
 * Schema generated from (https://oe3meta.orf.at/oe3api/ApiV2.php/TrafficInfo.json)
 *
 * @author Michael Ratzenböck
 * @since 2020
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"TrafficItems"})
public class TrafficItemsDTO {

    @JsonProperty("trafficItems")
    @JsonAlias("TrafficItems")
    public final List<TrafficItemDTO> trafficItems = new ArrayList<>();
    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

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
        return new ToStringBuilder(this).append("trafficItems", trafficItems)
                                        .append("additionalProperties", additionalProperties)
                                        .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(trafficItems)
                                    .append(additionalProperties)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof TrafficItemsDTO rhs)) {
            return false;
        }
        return new EqualsBuilder().append(trafficItems, rhs.trafficItems)
                                  .append(additionalProperties, rhs.additionalProperties)
                                  .isEquals();
    }

}
