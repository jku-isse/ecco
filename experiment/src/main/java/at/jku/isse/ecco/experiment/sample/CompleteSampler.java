package at.jku.isse.ecco.experiment.sample;

import at.jku.isse.ecco.experiment.utils.CollectionUtils;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import org.variantsync.vevos.simulation.feature.Variant;
import org.variantsync.vevos.simulation.feature.config.SimpleConfiguration;
import org.variantsync.vevos.simulation.feature.sampling.Sample;
import org.variantsync.vevos.simulation.feature.sampling.Sampler;
import org.variantsync.vevos.simulation.util.names.NameGenerator;
import org.variantsync.vevos.simulation.util.names.NumericNameGenerator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * Sampler that creates all possible variants of an SPL.
 */
public class CompleteSampler implements Sampler {
    private final NameGenerator variantNameGenerator;

    public CompleteSampler() {
        this.variantNameGenerator = new NumericNameGenerator("Variant");
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Sample sample(IFeatureModel model) {
        List<IFeature> features = new ArrayList<>(model.getFeatures());
        return sample(features);
    }

    public Sample sample(List<IFeature> features) {
        Set<String> allFeatureNames = features.stream().map(IFeatureModelElement::getName).collect(Collectors.toSet());
        Set<Set<String>> powerset = CollectionUtils.powerSet(allFeatureNames);

        final AtomicInteger variantNo = new AtomicInteger();
        List<Variant> variants = new LinkedList<>();
        for (Set<String> variantFeatureNames : powerset) {
            variants.add(new Variant(this.variantNameGenerator.getNameAtIndex(variantNo.getAndIncrement()), new SimpleConfiguration(new ArrayList<>(variantFeatureNames))));
        }

        return new Sample(variants);
    }
}
