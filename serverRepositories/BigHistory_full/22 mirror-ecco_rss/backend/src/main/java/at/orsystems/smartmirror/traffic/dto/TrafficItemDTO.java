package at.orsystems.smartmirror.traffic.dto;

import at.orsystems.smartmirror.common.dto.CoordinateDTO;
import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

/**
 * Represents a single traffic notification message from https://oe3meta.orf.at/oe3api/ApiV2.php/TrafficInfo.json.
 * <p>
 * <bf>Note:</bf> Jackson uses the variable name for generating the JSON object when serializing it.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"Text", "Street", "District", "EventCode", "EventImage", "Coordinates"})
public class TrafficItemDTO {
    @NonNull
    public final List<CoordinateDTO> coordinates;
    @JsonIgnore
    @NonNull
    public final Map<String, Object> additionalProperties = new HashMap<>();
    @NonNull
    public final String text;
    @NonNull
    public final String street;
    @NonNull
    public final String district;
    public final int eventCode;
    @NonNull
    public final String eventImageUrl;

    @JsonCreator
    public TrafficItemDTO(@JsonProperty(value = "text", required = true) @JsonAlias("Text") @NonNull String text,
                          @JsonProperty(value = "street", required = true) @JsonAlias("Street") @NonNull String street,
                          @JsonProperty(value = "district", required = true) @JsonAlias("District") @NonNull String district,
                          @JsonProperty(value = "eventCode", required = true) @JsonAlias("EventCode") int eventCode,
                          @JsonProperty(value = "eventImageUrl", required = true) @JsonAlias("EventImage") @NonNull String eventImage,
                          @JsonProperty(value = "coordinates", required = true) @JsonAlias("Coordinates") @NonNull List<CoordinateDTO> coordinates) {

        this.text = text;
        this.street = street;
        this.district = district;
        this.eventCode = eventCode;
        this.coordinates = unmodifiableList(coordinates);
        this.eventImageUrl = eventImage;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return unmodifiableMap(additionalProperties);
    }

    @JsonAnySetter
    public void setAdditionalProperty(@NonNull String name, @NonNull Object value) {
        requireNonNull(name);
        requireNonNull(value);
        additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("text", text)
                                        .append("street", street)
                                        .append("district", district)
                                        .append("eventCode", eventCode)
                                        .append("eventImage", eventImageUrl)
                                        .append("coordinates", coordinates)
                                        .append("additionalProperties", additionalProperties)
                                        .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(eventCode)
                                    .append(eventImageUrl)
                                    .append(street)
                                    .append(district)
                                    .append(coordinates)
                                    .append(text)
                                    .append(additionalProperties)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof TrafficItemDTO rhs)) {
            return false;
        }
        return new EqualsBuilder().append(eventCode, rhs.eventCode)
                                  .append(eventImageUrl, rhs.eventImageUrl)
                                  .append(street, rhs.street)
                                  .append(district, rhs.district)
                                  .append(coordinates, rhs.coordinates)
                                  .append(text, rhs.text)
                                  .append(additionalProperties, rhs.additionalProperties)
                                  .isEquals();
    }

}
