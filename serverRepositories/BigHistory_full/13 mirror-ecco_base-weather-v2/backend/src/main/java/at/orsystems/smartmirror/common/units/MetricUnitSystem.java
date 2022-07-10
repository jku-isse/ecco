package at.orsystems.smartmirror.common.units;

/**
 * Provides the units the metric unit system uses.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
public class MetricUnitSystem extends UnitSystem {
    @Override
    public String speedUnit() {
        return Unit.KILOMETER_PER_HOUR.getRepresentation();
    }

    @Override
    public String temperatureUnit() {
        return Unit.CELSIUS.getRepresentation();
    }
}
