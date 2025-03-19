package mistake;

import at.jku.isse.ecco.experiment.mistake.MistakeCreator;
import at.jku.isse.ecco.experiment.mistake.MistakeStrategy;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import mistake.stubs.FeatureTraceStub;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;


public class MistakeCreatorTest {

    String FAULTY_CONDITION = "faulty condition";
    String CORRECT_CONDITION = "correct condition";
    String DIFFERENT_CORRECT_CONDITION = "different correct condition";

    @Mock
    Repository.Op repositoryMock;

    MistakeStrategy mistakeStrategyStub = new MistakeStrategy() {
        @Override
        protected String createNewMistake(FeatureTrace trace) {
            trace.setProactiveCondition(FAULTY_CONDITION);
            return FAULTY_CONDITION;
        }
    };


    @Test
    public void originalConditionsAreRestored(){
        FeatureTrace firstTrace = new FeatureTraceStub(CORRECT_CONDITION);
        FeatureTrace secondTrace = new FeatureTraceStub(CORRECT_CONDITION);
        Collection<FeatureTrace> traceCollection = new ArrayList<>(2);
        traceCollection.add(firstTrace);
        traceCollection.add(secondTrace);

        MistakeCreator mistakeCreator = new MistakeCreator(mistakeStrategyStub);
        mistakeCreator.createMistakePercentage(repositoryMock, traceCollection, 100);
        mistakeCreator.restoreOriginalConditions();

        assertEquals(2, traceCollection.stream().filter(trace -> trace.getProactiveConditionString().equals(CORRECT_CONDITION)).toList().size());
    }

    @Test
    public void originalConditionsAreRestoredForSameMistakesAndDifferentOriginalConditions(){
        FeatureTrace firstTrace = new FeatureTraceStub(CORRECT_CONDITION);
        FeatureTrace secondTrace = new FeatureTraceStub(DIFFERENT_CORRECT_CONDITION);
        Collection<FeatureTrace> traceCollection = new ArrayList<>(2);
        traceCollection.add(firstTrace);
        traceCollection.add(secondTrace);

        MistakeCreator mistakeCreator = new MistakeCreator(mistakeStrategyStub);
        mistakeCreator.createMistakePercentage(repositoryMock, traceCollection, 100);
        mistakeCreator.restoreOriginalConditions();

        assertEquals(CORRECT_CONDITION, firstTrace.getProactiveConditionString());
        assertEquals(DIFFERENT_CORRECT_CONDITION, secondTrace.getProactiveConditionString());
    }
}
