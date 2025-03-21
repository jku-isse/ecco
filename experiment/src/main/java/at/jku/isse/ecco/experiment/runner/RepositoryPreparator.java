package at.jku.isse.ecco.experiment.runner;

import at.jku.isse.ecco.experiment.mistake.MistakeCreator;
import at.jku.isse.ecco.experiment.picker.ListPicker;
import at.jku.isse.ecco.experiment.picker.MemoryListPicker;
import at.jku.isse.ecco.experiment.utils.tracecollector.FeatureTraceCollector;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.featuretrace.SerFeatureTrace;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

public class RepositoryPreparator {

    private final MistakeCreator mistakeCreator;
    private final ListPicker<FeatureTrace> listPicker;
    private Collection<FeatureTrace> allProactiveTraces;
    private Collection<FeatureTrace> nonEvaluableProactiveTraces;

    public RepositoryPreparator(MistakeCreator mistakeCreator,
                                MemoryListPicker<FeatureTrace> listPicker){
        this.mistakeCreator = mistakeCreator;
        this.listPicker = listPicker;
    }

    public void prepareRepository(Repository.Op repository,
                                  int featureTracePercentage,
                                  int mistakePercentage,
                                  GroundTruth groundTruth){
        FeatureTraceCollector collector = new FeatureTraceCollector(repository, groundTruth);
        this.allProactiveTraces = collector.getFeatureTraces();
        Collection<FeatureTrace> evaluableProactiveTraces = collector.getEvaluableTraces();
        this.nonEvaluableProactiveTraces = collector.getNonEvaluableTraces();
        Collection<FeatureTrace> keptProactiveTraces = this.keepFeatureTracePercentage(evaluableProactiveTraces, featureTracePercentage);
        this.mistakeCreator.createMistakePercentage(repository, keptProactiveTraces, mistakePercentage);
        this.removeNonEvaluableTraces();
    }

    public void undoPreparation(){
        this.mistakeCreator.restoreOriginalConditions();
        this.restoreFeatureTraces(this.allProactiveTraces);
    }

    private Collection<FeatureTrace> keepFeatureTracePercentage(Collection<FeatureTrace> allProactiveTraces, int percentage) {
        if (percentage < 0 || percentage > 100){
            throw new RuntimeException(String.format("Percentage of feature traces is invalid (%d).", percentage));
        }
        List<FeatureTrace> remainingTraces = this.listPicker.pickPercentage(allProactiveTraces, percentage);
        List<FeatureTrace> tracesToBeRemoved = new LinkedList<>(allProactiveTraces);
        tracesToBeRemoved.removeAll(remainingTraces);
        tracesToBeRemoved.forEach(this::removeTrace);
        return remainingTraces;
    }

    private void removeNonEvaluableTraces(){
        this.nonEvaluableProactiveTraces.forEach(this::removeTrace);
    }

    private void removeTrace(FeatureTrace trace){
        FeatureTrace newTrace = new SerFeatureTrace(trace.getNode());
        newTrace.setRetroactiveCondition(trace.getRetroactiveConditionString());
        Node.Op node = (Node.Op) trace.getNode();
        node.setFeatureTrace(newTrace);
    }

    private void restoreFeatureTraces(Collection<FeatureTrace> traces){
        for (FeatureTrace featureTrace: traces){
            Node.Op node = (Node.Op) featureTrace.getNode();
            node.setFeatureTrace(featureTrace);
        }
    }
}
