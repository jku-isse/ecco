package at.orsystems.smartmirror.common.units;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
public abstract class UnitSystem {
    public abstract String speedUnit();

    public abstract String temperatureUnit();

    public String percentageUnit() {
        return Unit.PERCENTAGE.getRepresentation();
    }

    public String pressureUnit() {
        return Unit.HECTO_PASCAL.getRepresentation();
    }

    /**
     * Defines the different unit systems.
     *
     * @author Michael Ratzenböck
     * @since 2020
     */
    public enum Type {
        METRIC,
        IMPERIAL,
        NONE;

        public static Type getTypeFor(String type) {
            if (METRIC.name().equalsIgnoreCase(type))
                return METRIC;
            else if (IMPERIAL.name().equalsIgnoreCase(type))
                return IMPERIAL;
            else
                return NONE;
        }
    }
}
