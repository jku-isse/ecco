package at.orsystems.smartmirror.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * This is the @{@link RestController} that is responsible for making the global application settings available to the
 * frontend.
 *
 * @author Michael Ratzenböck
 * @since 2021
 */
@RestController
public class SettingsController {
    @NotNull
    private final SettingsService service;

    public SettingsController(@NotNull SettingsService service) {
        this.service = requireNonNull(service);
    }

    @GetMapping("/settings")
    public SettingsDTO getSettings() {
        return service.getSettings();
    }
}
