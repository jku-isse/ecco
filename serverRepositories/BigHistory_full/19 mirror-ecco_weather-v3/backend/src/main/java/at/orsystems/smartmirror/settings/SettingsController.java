package at.orsystems.smartmirror.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;

import static java.util.Objects.requireNonNull;

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
