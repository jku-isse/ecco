package at.orsystems.smartmirror.settings;

import at.orsystems.smartmirror.startup.SmartMirrorProperties;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * The service that is responsible for providing the global application settings.
 *
 * @author Michael Ratzenböck
 * @since 2021
 */
@Service
public class SettingsService {
    @NotNull
    private final SmartMirrorProperties properties;

    public SettingsService(@NotNull SmartMirrorProperties properties) {
        this.properties = requireNonNull(properties);
    }

    /**
     * The global application settings that can be sent to the frontend.
     */
    public SettingsDTO getSettings() {
        return new SettingsDTO(this.properties);
    }
}
