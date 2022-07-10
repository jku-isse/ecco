package at.orsystems.smartmirror.weather.dto;

import at.orsystems.smartmirror.common.dto.CoordinateDTO;
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
@JsonPropertyOrder({"coord", "weather", "base", "main", "visibility", "wind", "clouds", "dt", "sys", "timezone", "id"
        , "name", "cod"})
public class Weather {

    @JsonProperty("coord")
    private CoordinateDTO coord;
    @JsonProperty("weather")
    private List<Weather_> weather = new ArrayList<Weather_>();
    @JsonProperty("base")
    private String base;
    @JsonProperty("main")
    private Main main;
    @JsonProperty("visibility")
    private long visibility;
    @JsonProperty("wind")
    private Wind wind;
    @JsonProperty("clouds")
    private Clouds clouds;
    @JsonProperty("dt")
    private long dt;
    @JsonProperty("sys")
    private Sys sys;
    @JsonProperty("timezone")
    private long timezone;
    @JsonProperty("id")
    private long id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("cod")
    private long cod;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();
    
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
        return new ToStringBuilder(this).append("coord", coord)
                                        .append("weather", weather)
                                        .append("base", base)
                                        .append("main", main)
                                        .append("visibility", visibility)
                                        .append("wind", wind)
                                        .append("clouds", clouds)
                                        .append("dt", dt)
                                        .append("sys", sys)
                                        .append("timezone", timezone)
                                        .append("id", id)
                                        .append("name", name)
                                        .append("cod", cod)
                                        .append("additionalProperties", additionalProperties)
                                        .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(visibility)
                                    .append(timezone)
                                    .append(main)
                                    .append(clouds)
                                    .append(sys)
                                    .append(dt)
                                    .append(coord)
                                    .append(weather)
                                    .append(name)
                                    .append(cod)
                                    .append(id)
                                    .append(additionalProperties)
                                    .append(base)
                                    .append(wind)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Weather) == false) {
            return false;
        }
        Weather rhs = ((Weather) other);
        return new EqualsBuilder().append(visibility, rhs.visibility)
                                  .append(timezone, rhs.timezone)
                                  .append(main, rhs.main)
                                  .append(clouds, rhs.clouds)
                                  .append(sys, rhs.sys)
                                  .append(dt, rhs.dt)
                                  .append(coord, rhs.coord)
                                  .append(weather, rhs.weather)
                                  .append(name, rhs.name)
                                  .append(cod, rhs.cod)
                                  .append(id, rhs.id)
                                  .append(additionalProperties, rhs.additionalProperties)
                                  .append(base, rhs.base)
                                  .append(wind, rhs.wind)
                                  .isEquals();
    }

}
