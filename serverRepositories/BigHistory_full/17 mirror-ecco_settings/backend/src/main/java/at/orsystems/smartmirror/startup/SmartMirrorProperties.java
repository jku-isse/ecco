package at.orsystems.smartmirror.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Validated
@ConstructorBinding
@ConfigurationProperties(prefix = "smartmirror")
public class SmartMirrorProperties {
    @NotBlank
    @NotNull
    public final String language;

    public SmartMirrorProperties(final String language) {
        this.language = language;
    }

    public String getLanguage() {
        return this.language;
    }
}
