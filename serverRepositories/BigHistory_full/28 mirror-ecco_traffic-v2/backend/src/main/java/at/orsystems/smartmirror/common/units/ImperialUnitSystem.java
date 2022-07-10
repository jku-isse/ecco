package at.orsystems.smartmirror.common.units;

/**
 * Provides the units the imperial unit system uses.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
public class ImperialUnitSystem extends UnitSystem {
    @Override
    public String speedUnit() {
        return Unit.MILES_PER_HOUR.getRepresentation();
    }

    @Override
    public String temperatureUnit() {
        return Unit.FAHRENHEIT.getRepresentation();
    }
}
