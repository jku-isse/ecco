package at.jku.isse.ecco.experiment.runner;

import at.jku.isse.ecco.experiment.mistake.MistakeCreator;
import at.jku.isse.ecco.experiment.picker.ListPicker;
import at.jku.isse.ecco.experiment.picker.MemoryListPicker;
import at.jku.isse.ecco.experiment.utils.tracecollector.FeatureTraceCollector;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.featuretrace.MemFeatureTrace;
import at.jku.isse.ecco.tree.Node;

import java.util.*;
import java.util.logging.Logger;

public class RepositoryPreparator {

    private static final Logger logger = Logger.getLogger(RepositoryPreparator.class.getName());

    private MistakeCreator mistakeCreator;
    private ListPicker<FeatureTrace> listPicker;
    private Collection<FeatureTrace> allProactiveTraces;
    private Collection<FeatureTrace> remainingProactiveTraces;

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
        this.remainingProactiveTraces = this.removeFeatureTracePercentage(allProactiveTraces, 100 - featureTracePercentage);
        this.mistakeCreator.createMistakePercentage(repository, this.remainingProactiveTraces, mistakePercentage);
    }

    public void undoPreparation(){
        this.mistakeCreator.restoreOriginalConditions();
        this.restoreFeatureTraces(allProactiveTraces);
    }

    private Collection<FeatureTrace> removeFeatureTracePercentage(Collection<FeatureTrace> allProactiveTraces, int percentage) {
        if (percentage < 0 || percentage > 100){
            throw new RuntimeException(String.format("Percentage of feature traces is invalid (%d).", percentage));
        }
        List<FeatureTrace> tracesToBeRemoved = this.listPicker.pickPercentage(allProactiveTraces, percentage);
        List<FeatureTrace> remainingTraces = new LinkedList<>(allProactiveTraces);
        remainingTraces.removeAll(tracesToBeRemoved);
        for (FeatureTrace featureTrace : tracesToBeRemoved){
            FeatureTrace newTrace = new MemFeatureTrace(featureTrace.getNode());
            newTrace.setDiffCondition(featureTrace.getDiffConditionString());
            Node.Op node = (Node.Op) featureTrace.getNode();
            node.setFeatureTrace(newTrace);
        }
        return remainingTraces;
    }

    private void restoreFeatureTraces(Collection<FeatureTrace> traces){
        for (FeatureTrace featureTrace: traces){
            Node.Op node = (Node.Op) featureTrace.getNode();
            node.setFeatureTrace(featureTrace);
        }
    }

    public Collection<FeatureTrace> getAllProactiveTraces(){
        return this.allProactiveTraces;
    }

    public Collection<FeatureTrace> getRemainingProactiveTraces(){
        return this.remainingProactiveTraces;
    }

}
