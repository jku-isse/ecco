package at.orsystems.smartmirror.weather.owm.forecast;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableList;

/**
 * Represents an openweathermap forecast response.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"city", "cod", "message", "cnt", "list"})
public class WeatherForecast {
    /**
     * Contains information about the city of the weather station.
     */
    public final City city;
    /**
     * Some internal parameter. Optional.
     */
    public final String cod;
    /**
     * Some internal parameter. Optional.
     */
    public final Double message;
    /**
     * The amount of forecast days.
     */
    public final long forecastDays;
    /**
     * The actual weather of each forecast day.
     */
    public final List<ForecastDay> forecastWeatherDays;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonCreator
    public WeatherForecast(@JsonProperty(value = "city", required = true) City city,
                           @JsonProperty(value = "cod") String cod,
                           @JsonProperty(value = "message") Double message,
                           @JsonProperty(value = "forecastDays", required = true) @JsonAlias("cnt") long forecastDays,
                           @JsonProperty(value = "forecastWeatherDays", required = true) @JsonAlias("list") List<ForecastDay> forecastWeatherDays) {
        this.city = city;
        this.cod = cod;
        this.message = message;
        this.forecastDays = forecastDays;
        this.forecastWeatherDays = unmodifiableList(forecastWeatherDays);
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
        return new ToStringBuilder(this).append("city", city)
                .append("cod", cod)
                .append("message", message)
                .append("forecastDays", forecastDays)
                .append("forecastWeatherDays", forecastWeatherDays)
                .append("additionalProperties", additionalProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(city)
                .append(forecastDays)
                .append(cod)
                .append(additionalProperties)
                .append(message)
                .append(forecastWeatherDays)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof WeatherForecast)) {
            return false;
        }

        final var rhs = (WeatherForecast) other;

        return new EqualsBuilder().append(city, rhs.city)
                .append(forecastDays, rhs.forecastDays)
                .append(cod, rhs.cod)
                .append(additionalProperties, rhs.additionalProperties)
                .append(message, rhs.message)
                .append(forecastWeatherDays, rhs.forecastWeatherDays)
                .isEquals();
    }
}
