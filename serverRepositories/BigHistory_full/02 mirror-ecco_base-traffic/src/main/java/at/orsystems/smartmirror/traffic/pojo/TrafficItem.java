package at.orsystems.smartmirror.traffic.pojo;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"Text", "Street", "District", "EventCode", "EventImage", "Coordinates"})
public class TrafficItem {

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();
    @JsonProperty("Text")
    public String text;
    @JsonProperty("Street")
    public String street;
    @JsonProperty("District")
    public String district;
    @JsonProperty("EventCode")
    public int eventCode;
    @JsonProperty("EventImage")
    public String eventImage;
    @JsonProperty("Coordinates")
    public final List<Coordinate> coordinates = new ArrayList<>();

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
        return new ToStringBuilder(this).append("text", text).append("street", street).append("district", district)
                .append("eventCode", eventCode).append("eventImage", eventImage).append("coordinates", coordinates)
                .append("additionalProperties", additionalProperties).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(eventCode).append(eventImage).append(street).append(district)
                .append(coordinates).append(text).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof TrafficItem rhs)) {
            return false;
        }
        return new EqualsBuilder().append(eventCode, rhs.eventCode).append(eventImage, rhs.eventImage)
                .append(street, rhs.street).append(district, rhs.district).append(coordinates, rhs.coordinates)
                .append(text, rhs.text).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
