package at.orsystems.smartmirror.weather.owm.forecast;

import at.orsystems.smartmirror.common.dto.CoordinateDTO;
import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the CITY JSON object from openweathermap.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "name", "coordinates", "country", "population", "timezone"})
public class City {
    /**
     * The openweathermap id of the city.
     */
    public final long id;
    /**
     * The name of the city.
     */
    public final String name;
    /**
     * The coordinates of the city.
     */
    public final CoordinateDTO coordinates;
    /**
     * The name of the country of the city.
     */
    public final String country;
    /**
     * The population (some internal parameter). The wrapper class is used because this is an optional property.
     * Hence, it would not be included when serializing this object.
     */
    public final Long population;
    /**
     * The timezone.The wrapper class is used because this is an optional property.
     * Hence, it would not be included when serializing this object.
     */
    public final Long timezone;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public City(@JsonProperty(value = "id", required = true) long id,
                @JsonProperty(value = "name", required = true) String name,
                @JsonProperty(value = "coordinates", required = true) @JsonAlias("coord") CoordinateDTO coordinates,
                @JsonProperty(value = "country", required = true) String country,
                @JsonProperty(value = "population") Long population,
                @JsonProperty(value = "timezone") Long timezone) {
        this.id = id;
        this.name = name;
        this.coordinates = coordinates;
        this.country = country;
        this.population = population;
        this.timezone = timezone;
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
        return new ToStringBuilder(this).append("id", id)
                .append("name", name)
                .append("coordinates", coordinates)
                .append("country", country)
                .append("population", population)
                .append("timezone", timezone)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(country)
                .append(coordinates)
                .append(timezone)
                .append(name)
                .append(id)
                .append(additionalProperties)
                .append(population)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof City)) {
            return false;
        }

        final var rhs = (City) other;
        return new EqualsBuilder().append(country, rhs.country)
                .append(coordinates, rhs.coordinates)
                .append(timezone, rhs.timezone)
                .append(name, rhs.name)
                .append(id, rhs.id)
                .append(additionalProperties, rhs.additionalProperties)
                .append(population, rhs.population)
                .isEquals();
    }
}
