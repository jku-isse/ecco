package at.jku.isse.ecco.experiment.picker.featuretracepicker;

import at.jku.isse.ecco.experiment.picker.MemoryListPicker;
import at.jku.isse.ecco.featuretrace.FeatureTrace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RandomFeatureTracePicker implements MemoryListPicker<FeatureTrace> {

    private List<FeatureTrace> source;

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
        return featureTraceList.subList(0, numberOfPicks);
    }

    @Override
    public List<FeatureTrace> getSource() {
        return this.source;
    }
}
