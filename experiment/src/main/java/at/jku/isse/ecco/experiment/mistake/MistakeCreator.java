package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import org.tinylog.Logger;

import java.util.*;

public class MistakeCreator {

    private final MistakeStrategy mistakeStrategy;

    private Map<FeatureTrace, String> originalConditions;

    public MistakeCreator(MistakeStrategy mistakeStrategy) {
        this.mistakeStrategy = mistakeStrategy;
    }

    public int createMistakePercentage(Repository.Op repository, Collection<FeatureTrace> featureTraces, int percentage){
        // return the number of mistakes that are missing to reach the given percentage
        int mistakesCreated = 0;
        this.originalConditions = new HashMap<>();
        this.mistakeStrategy.init(repository);
        if (percentage < 0 || percentage > 100){
            throw new RuntimeException(String.format("Percentage of feature traces is invalid (%d).", percentage));
        }
        int noOfMistakes = (featureTraces.size() * percentage) / 100;
        List<FeatureTrace> featureTraceList = new ArrayList<>(featureTraces);
        Collections.shuffle(featureTraceList);
        Iterator<FeatureTrace> iterator = featureTraceList.stream().iterator();
        int attempts = noOfMistakes;

        for (int i = 1; i <= attempts; i++){
            if (!iterator.hasNext()){
                Logger.info("Failed to create enough mistakes!");
                return noOfMistakes - mistakesCreated;
            }
            FeatureTrace trace = iterator.next();
            String originalCondition = trace.getUserConditionString();
            if (!this.attemptMistake(trace)){
                attempts++;
            } else {
                this.originalConditions.put(trace, originalCondition);
                mistakesCreated++;
            }
        }
        return 0;
    }

    public void restoreOriginalConditions(){
        for (FeatureTrace trace : this.originalConditions.keySet()){
            String originalCondition = this.originalConditions.get(trace);
            trace.setUserCondition(originalCondition);
        }
    }

    private boolean attemptMistake(FeatureTrace trace){
        try{
            this.mistakeStrategy.createMistake(trace);
            return true;
        } catch (Exception e){
            return false;
        }
    }

    public Collection<FeatureTrace> getFaultyTraces(){
        return this.originalConditions.keySet();
    }
}
