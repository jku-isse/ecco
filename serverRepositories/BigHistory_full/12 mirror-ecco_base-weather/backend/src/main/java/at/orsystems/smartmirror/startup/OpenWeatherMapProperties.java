package at.orsystems.smartmirror.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * Holds and validates all the property values for the openweathermap plugin.
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
    @NotBlank
    @NotNull
    public final String language;

    public OpenWeatherMapProperties(String API, String version, String units, String language) {
        this.API = API;
        this.version = version;
        this.units = units;
        this.language = language;
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

    public String getLanguage() {
        return language;
    }
}
