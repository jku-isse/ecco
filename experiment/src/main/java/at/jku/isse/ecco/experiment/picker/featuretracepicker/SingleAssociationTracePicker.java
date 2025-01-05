package at.jku.isse.ecco.experiment.picker.featuretracepicker;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.experiment.picker.MemoryListPicker;
import at.jku.isse.ecco.featuretrace.FeatureTrace;

import java.util.*;
import java.util.stream.Collectors;

public class SingleAssociationTracePicker implements MemoryListPicker<FeatureTrace> {

    private List<FeatureTrace> source;

    private List<FeatureTrace> pickSingleAssociationTraces(Collection<FeatureTrace> source){
        this.source = source.stream().toList();
        List<FeatureTrace> featureTraceList = new ArrayList<>();
        Set<Association> associations = source.stream().map(trace -> trace.getNode().getContainingAssociation()).collect(Collectors.toSet());

        for (Association association : associations){
            List<FeatureTrace> associationTraces = new ArrayList<>(source.stream()
                    .filter(trace -> trace.getNode().getContainingAssociation() == association)
                    .toList());
            if (associationTraces.size() > 0) {
                Collections.shuffle(associationTraces);
                featureTraceList.add(associationTraces.getFirst());
            }
        }

        return featureTraceList;
    }

    @Override
    public List<FeatureTrace> pickPercentage(Collection<FeatureTrace> source, int percentage) {
        return this.pickSingleAssociationTraces(source);
    }

    @Override
    public List<FeatureTrace> pickNumber(Collection<FeatureTrace> source, int numberOfPicks) {
        return this.pickSingleAssociationTraces(source);
    }

    @Override
    public List<FeatureTrace> getSource() {
        return this.source;
    }
}
