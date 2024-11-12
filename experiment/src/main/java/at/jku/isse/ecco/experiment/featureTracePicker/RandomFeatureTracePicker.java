package at.jku.isse.ecco.experiment.featureTracePicker;

import at.jku.isse.ecco.experiment.utils.picker.MemoryListPicker;
import at.jku.isse.ecco.featuretrace.FeatureTrace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RandomFeatureTracePicker implements MemoryListPicker<FeatureTrace> {

    private List<FeatureTrace> source;
    private List<FeatureTrace> pick;

    @Override
    public List<FeatureTrace> pickPercentage(Collection<FeatureTrace> source, int percentage) {
        int numberOfPicks = (source.size() * percentage) / 100;
        return this.pickNumber(source, numberOfPicks);
    }

    @Override
    public List<FeatureTrace> pickNumber(Collection<FeatureTrace> source, int numberOfPicks) {
        List<FeatureTrace> featureTraceList = new ArrayList<>(source);
        this.source = featureTraceList;
        Collections.shuffle(featureTraceList);
        this.pick = featureTraceList.subList(0, numberOfPicks);
        return this.pick;
    }

    @Override
    public List<FeatureTrace> getSource() {
        return this.source;
    }
}
