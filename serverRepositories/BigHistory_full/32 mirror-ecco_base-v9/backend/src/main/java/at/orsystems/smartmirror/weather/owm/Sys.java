package at.orsystems.smartmirror.weather.owm;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Represents the sys JSON object from openweathermap.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "id", "country", "sunrise", "sunset"})
public class Sys {
    /**
     * Internal parameter from the weather station.
     */
    public final Long type;
    /**
     * Internal parameter from the weather station.
     */
    public final Long id;
    /**
     * The country code of the weather station (e.g. GB, JP,...)
     */
    public final String countryId;
    /**
     * The sunrise time, unix, UTC
     */
    public final long sunrise;
    /**
     * The sunset time, unix, UTC
     */
    public final long sunset;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public Sys(@JsonProperty(value = "type") Long type,
               @JsonProperty(value = "id") Long id,
               @JsonProperty(value = "countryId") @JsonAlias("country") String countryId,
               @JsonProperty(value = "sunrise", required = true) long sunrise,
               @JsonProperty(value = "sunset", required = true) long sunset) {
        this.type = type;
        this.id = id;
        this.countryId = countryId;
        this.sunrise = sunrise;
        this.sunset = sunset;
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
        return new ToStringBuilder(this).append("type", type)
                .append("id", id)
                .append("countryId", countryId)
                .append("sunrise", sunrise)
                .append("sunset", sunset)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(countryId)
                .append(sunrise)
                .append(sunset)
                .append(id)
                .append(additionalProperties)
                .append(type)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Sys)) {
            return false;
        }

        final var rhs = (Sys) other;

        return new EqualsBuilder().append(countryId, rhs.countryId)
                .append(sunrise, rhs.sunrise)
                .append(sunset, rhs.sunset)
                .append(id, rhs.id)
                .append(additionalProperties, rhs.additionalProperties)
                .append(type, rhs.type)
                .isEquals();
    }
}
