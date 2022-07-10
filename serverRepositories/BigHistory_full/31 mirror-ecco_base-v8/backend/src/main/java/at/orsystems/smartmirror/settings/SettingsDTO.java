package at.orsystems.smartmirror.settings;

import at.orsystems.smartmirror.startup.SmartMirrorProperties;

import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 * This is the objects that gets transmitted to the frontend which contains the global settings of the application.
 *
 * @author Michael Ratzenböck
 * @since 2021
 */
public class SettingsDTO {
    /**
     * Contains the language code for the entire application and all its submodules.
     */
    @NotNull
    public final String language;

    public SettingsDTO(@NotNull SmartMirrorProperties smartMirrorProperties) {
        Objects.requireNonNull(smartMirrorProperties);

        this.language = smartMirrorProperties.language;
    }
}
