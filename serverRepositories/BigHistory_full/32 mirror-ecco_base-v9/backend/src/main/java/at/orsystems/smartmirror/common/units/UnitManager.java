package at.orsystems.smartmirror.common.units;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * The class that provides the UnitSystem Bean.
 *
 * @author Michael Ratzenböck
 * @since 2020
 */
@Configuration
public class UnitManager {
    @Value("${openweathermap.units}")
    private String unitSystemName;

    @Bean
    @Scope("singleton")
    public UnitSystem getUnitSystem() {
        UnitSystem.Type type = UnitSystem.Type.getTypeFor(unitSystemName);

        switch (type) {
            case METRIC:
                return new MetricUnitSystem();
            case IMPERIAL:
                return new ImperialUnitSystem();
            case NONE:
                return new OpenWeatherMapDefaultUnitSystem();
        }

        return new MetricUnitSystem();
    }
}


