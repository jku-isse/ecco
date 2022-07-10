package at.orsystems.smartmirror.weather.owm;

import at.orsystems.smartmirror.common.dto.CoordinateDTO;
import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a response from openweathermap when requesting the current weather.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"coordinates",
        "weatherDetails",
        "mainInformation",
        "visibility",
        "wind",
        "clouds",
        "requestDate",
        "system",
        "timezone",
        "cityId",
        "cityName"})
public class CurrentWeather {
    /**
     * Contains the coordinates of the weather station.
     */
    public final CoordinateDTO coordinates;
    /**
     * Contains further weather condition information.
     */
    public final List<WeatherDetails> weatherDetails;
    /**
     * Contains the main information about the weather like temperature, humidity,...
     */
    public final Main mainInformation;
    /**
     * The visibility in meters (whatever this means)
     */
    public final Double visibility;
    /**
     * Contains information about the wind like speed, direction,...
     */
    public final Wind wind;
    /**
     * Contains information about the clouds.
     */
    public final Clouds clouds;
    /**
     * Contains the time of data calculation, unix, UTC
     */
    public final long requestDate;
    /**
     * Contains some internal parameters from the weather station as well as the country code and the sunrise/sunset
     * information.
     */
    public final Sys system;
    /**
     * Shift in seconds from the UTC
     */
    public final long timezone;
    /**
     * The id of the city.
     */
    public final long cityId;
    /**
     * The name of the city of the weather station.
     */
    public final String cityName;

    public CurrentWeather(@JsonProperty(value = "coordinates", required = true) @JsonAlias("coord") CoordinateDTO coordinates,
                          @JsonProperty(value = "weatherDetails", required = true) @JsonAlias("weather") List<WeatherDetails> weather,
                          @JsonProperty(value = "mainInformation", required = true) @JsonAlias("main") Main mainInformation,
                          @JsonProperty(value = "visibility") Double visibility,
                          @JsonProperty(value = "wind", required = true) Wind wind,
                          @JsonProperty(value = "clouds", required = true) Clouds clouds,
                          @JsonProperty(value = "requestDate", required = true) @JsonAlias("dt") long requestDate,
                          @JsonProperty(value = "system", required = true) @JsonAlias("sys") Sys system,
                          @JsonProperty(value = "timezone", required = true) long timezone,
                          @JsonProperty(value = "cityId", required = true) @JsonAlias("id") long cityId,
                          @JsonProperty(value = "cityName", required = true) @JsonAlias("name") String cityName) {
        this.coordinates = coordinates;
        this.weatherDetails = weather;
        this.mainInformation = mainInformation;
        this.visibility = visibility;
        this.wind = wind;
        this.clouds = clouds;
        this.requestDate = requestDate;
        this.system = system;
        this.timezone = timezone;
        this.cityId = cityId;
        this.cityName = cityName;
    }

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

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
        return new ToStringBuilder(this).append("coordinates", coordinates)
                .append("weatherDetails", weatherDetails)
                .append("mainInformation", mainInformation)
                .append("visibility", visibility)
                .append("wind", wind)
                .append("clouds", clouds)
                .append("requestDate", requestDate)
                .append("system", system)
                .append("timezone", timezone)
                .append("cityId", cityId)
                .append("cityName", cityName)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(visibility)
                .append(timezone)
                .append(mainInformation)
                .append(clouds)
                .append(system)
                .append(requestDate)
                .append(coordinates)
                .append(weatherDetails)
                .append(cityName)
                .append(cityId)
                .append(additionalProperties)
                .append(wind)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof CurrentWeather)) {
            return false;
        }

        final var rhs = (CurrentWeather) other;

        return new EqualsBuilder().append(visibility, rhs.visibility)
                .append(timezone, rhs.timezone)
                .append(mainInformation, rhs.mainInformation)
                .append(clouds, rhs.clouds)
                .append(system, rhs.system)
                .append(requestDate, rhs.requestDate)
                .append(coordinates, rhs.coordinates)
                .append(weatherDetails, rhs.weatherDetails)
                .append(cityName, rhs.cityName)
                .append(cityId, rhs.cityId)
                .append(additionalProperties, rhs.additionalProperties)
                .append(wind, rhs.wind)
                .isEquals();
    }

}
