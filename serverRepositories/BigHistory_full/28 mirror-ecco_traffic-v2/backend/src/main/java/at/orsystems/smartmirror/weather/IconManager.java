package at.orsystems.smartmirror.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This class holds the mapping between the openweathermap weather icons and our available icons.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
public final class IconManager {
    private static final IconManager INSTANCE = new IconManager();
    private static final Logger logger = LoggerFactory.getLogger(IconManager.class);
    private static final String path = "./icons/";
    private final Map<String, String> iconMap = new HashMap<>();

    private IconManager() {
        initMap();
    }

    public static IconManager instance() {
        return INSTANCE;
    }

    private void initMap() {
        iconMap.put("01d", "wi-day-sunny.svg");
        iconMap.put("01n", "wi-night-clear.svg");
        iconMap.put("02d", "wi-day-cloudy.svg");
        iconMap.put("02n", "wi-night-alt-cloudy.svg");
        iconMap.put("03d", "wi-cloud.svg");
        iconMap.put("03n", "wi-cloud.svg");
        iconMap.put("04d", "wi-cloudy.svg");
        iconMap.put("04n", "wi-cloudy.svg");
        iconMap.put("09d", "wi-showers.svg");
        iconMap.put("09n", "wi-showers.svg");
        iconMap.put("10d", "wi-day-rain-mix.svg");
        iconMap.put("10n", "wi-night-alt-showers.svg");
        iconMap.put("11d", "wi-thunderstorm.svg");
        iconMap.put("11n", "wi-thunderstorm.svg");
        iconMap.put("13d", "wi-snow.svg");
        iconMap.put("13n", "wi-snow.svg");
        iconMap.put("50d", "wi-fog.svg");
        iconMap.put("50n", "wi-fog.svg");
        iconMap.put("error", "error.svg");
    }

    /**
     * Translates the given openweathermap icon id into an a relative path for an equivalent icon of our own.
     *
     * @param id
     *         the openweathermap icon id
     * @return the relative path to an equivalent icon or the error icon if the id is unknown.
     */
    public String getPathForId(@NotNull final String id) {
        String ret;
        try {
            final int idNumber = Integer.parseInt(id);

            if (idNumber >= 300 && idNumber < 400) {
                ret = path + iconMap.get("09d");
            } else if (idNumber >= 500 && idNumber <= 504) {
                ret = path + iconMap.get("10d");
            } else if (idNumber == 511) {
                ret = path + iconMap.get("13d");
            } else if (idNumber >= 520 && idNumber < 600) {
                ret = path + iconMap.get("09d");
            } else if (idNumber >= 600 && idNumber < 700) {
                ret = path + iconMap.get("13d");
            } else if (idNumber >= 700 && idNumber < 800) {
                ret = path + iconMap.get("50d");
            } else {
                logger.warn("The idNumber {} is not supported.", idNumber);
                ret = path + iconMap.get("error");
            }
        } catch (final NumberFormatException ex) {
            String iconPath = iconMap.get(id);
            if (iconPath == null) {
                logger.warn("The id {} is not supported.", id);
                iconPath = iconMap.get("error");
            }
            ret = path + iconPath;
        }
        return ret;
    }
}
