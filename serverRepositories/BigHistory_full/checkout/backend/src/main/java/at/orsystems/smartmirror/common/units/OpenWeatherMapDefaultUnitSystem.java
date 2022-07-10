package at.orsystems.smartmirror.common.units;

/**
 * Provides the units that openweathermap uses if there is no unit system given.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
public class OpenWeatherMapDefaultUnitSystem extends UnitSystem {
    @Override
    public String speedUnit() {
        return Unit.KILOMETER_PER_HOUR.getRepresentation();
    }

    @Override
    public String temperatureUnit() {
        return Unit.KELVIN.getRepresentation();
    }
}
