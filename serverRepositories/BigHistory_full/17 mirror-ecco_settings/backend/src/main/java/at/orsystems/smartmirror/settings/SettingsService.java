package at.orsystems.smartmirror.settings;

import at.orsystems.smartmirror.startup.SmartMirrorProperties;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@Service
public class SettingsService {
    private final SmartMirrorProperties properties;

    public SettingsService(@NotNull SmartMirrorProperties properties) {
        this.properties = requireNonNull(properties);
    }

    public SettingsDTO getSettings() {
        return new SettingsDTO(this.properties);
    }
}
