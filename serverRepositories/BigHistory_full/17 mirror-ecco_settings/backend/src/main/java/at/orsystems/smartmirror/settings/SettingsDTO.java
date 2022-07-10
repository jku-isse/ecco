package at.orsystems.smartmirror.settings;

import at.orsystems.smartmirror.startup.SmartMirrorProperties;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class SettingsDTO {
    @NotNull
    public final String language;

    public SettingsDTO(@NotNull SmartMirrorProperties smartMirrorProperties) {
        Objects.requireNonNull(smartMirrorProperties);

        this.language = smartMirrorProperties.language;
    }
}
