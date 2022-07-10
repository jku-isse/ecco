package at.orsystems.smartmirror.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.*;

/**
 * Holds and validates all the property values for the openweathermap plugin.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@Validated
@ConstructorBinding
@ConfigurationProperties(prefix = "openweathermap")
public class OpenWeatherMapProperties {
    @NotBlank
    @NotNull
    public final String API;
    @NotBlank
    @NotNull
    public final String version;
    @Pattern(regexp = "metric|imperial", flags = Pattern.Flag.CASE_INSENSITIVE)
    public final String units;

    @Min(value = 1)
    @Max(value = 16)
    public final int forecastDays;

    public OpenWeatherMapProperties(String API, String version, String units, int forecastDays) {
        this.API = API;
        this.version = version;
        this.units = units;
        this.forecastDays = forecastDays;
    }

    /* The getters are needed for Spring... */
    public String getAPI() {
        return API;
    }

    public String getVersion() {
        return version;
    }

    public String getUnits() {
        return units;
    }

    public int getForecastDays() {
        return forecastDays;
    }
}
