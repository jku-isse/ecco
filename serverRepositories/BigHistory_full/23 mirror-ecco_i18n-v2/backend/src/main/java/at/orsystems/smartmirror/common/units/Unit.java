package at.orsystems.smartmirror.common.units;

/**
 * Defines all units that are used inside Smart Mirror.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
public enum Unit {
    KILOMETER_PER_HOUR("km/h"),
    MILES_PER_HOUR("mph"),
    PERCENTAGE("%"),
    HECTO_PASCAL("hPA"),
    FAHRENHEIT("F"),
    CELSIUS("°C"),
    KELVIN("K");

    private final String representation;

    Unit(String representation) {
        this.representation = representation;
    }

    public String getRepresentation() {
        return this.representation;
    }
}
