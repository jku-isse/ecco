package mistake;

import at.jku.isse.ecco.experiment.mistake.MistakeStrategy;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class MistakeStrategyTest {

    @Test
    public void mistakeStrategyRemembersMistakes(){
        FeatureTrace firstTrace = mock(FeatureTrace.class);
        when(firstTrace.getUserConditionString()).thenReturn("sameCondition");
        FeatureTrace secondTrace = mock(FeatureTrace.class);
        when(secondTrace.getUserConditionString()).thenReturn("sameCondition");

        MistakeStrategy mistakeStrategyStub = new MistakeStrategy() {
            private int mistakesCreated = 0;
            @Override
            protected String createNewMistake(FeatureTrace trace) {
                String result;
                if (mistakesCreated == 0) {
                    result = "FirstFaultyCondition";
                } else {
                    result = "FurtherFaultyCondition";
                }
                this.mistakesCreated++;
                return result;
            }
        };

        mistakeStrategyStub.createMistake(firstTrace);
        mistakeStrategyStub.createMistake(secondTrace);

        verify(secondTrace).setUserCondition("FirstFaultyCondition");
    }
}
