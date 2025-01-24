package at.jku.isse.ecco.experiment.sample;

import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.utils.DirUtils;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.experiment.utils.vevos.ConfigTransformer;
import at.jku.isse.ecco.experiment.utils.vevos.VevosUtils;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tinylog.Logger;
import org.variantsync.functjonal.Lazy;
import org.variantsync.functjonal.Result;
import org.variantsync.functjonal.list.NonEmptyList;
import org.variantsync.vevos.simulation.VEVOS;
import org.variantsync.vevos.simulation.feature.Variant;
import org.variantsync.vevos.simulation.feature.config.SimpleConfiguration;
import org.variantsync.vevos.simulation.feature.sampling.Sample;
import org.variantsync.vevos.simulation.feature.sampling.Sampler;
import org.variantsync.vevos.simulation.feature.sampling.SimpleSampler;
import org.variantsync.vevos.simulation.io.Resources;
import org.variantsync.vevos.simulation.repository.BusyboxRepository;
import org.variantsync.vevos.simulation.repository.SPLRepository;
import org.variantsync.vevos.simulation.util.io.CaseSensitivePath;
import org.variantsync.vevos.simulation.util.names.NumericNameGenerator;
import org.variantsync.vevos.simulation.variability.SPLCommit;
import org.variantsync.vevos.simulation.variability.VariabilityDataset;
import org.variantsync.vevos.simulation.variability.VariabilityHistory;
import org.variantsync.vevos.simulation.variability.pc.Artefact;
import org.variantsync.vevos.simulation.variability.pc.SourceCodeFile;
import org.variantsync.vevos.simulation.variability.pc.groundtruth.GroundTruth;
import org.variantsync.vevos.simulation.variability.pc.options.ArtefactFilter;
import org.variantsync.vevos.simulation.variability.pc.options.VariantGenerationOptions;
import org.variantsync.vevos.simulation.variability.sequenceextraction.LongestNonOverlappingSequences;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class VevosFeatureSampler {

    // todo: refactor

    private ExperimentRunConfiguration config;

    public VevosFeatureSampler(){
        VEVOS.Initialize();
    }

    public void sample(ExperimentRunConfiguration config, int noVariants) throws Resources.ResourceIOException, IOException {
        Logger.info("Sampling variants...");
        this.config = config;
        this.sample(noVariants);
    }

    private void sampleAllVariants(ExperimentRunConfiguration config) throws Resources.ResourceIOException, IOException {
        this.config = config;
        Sampler completeSampler = new CompleteSampler();
        this.sample(completeSampler);
    }

    private void sample(int noVariants) throws Resources.ResourceIOException, IOException {
        Sampler variantsSampler = SimpleSampler.CreateRandomSampler(noVariants, config.getMinVariantFeatures(), config.getMaxVariantFeatures());
        this.sample(variantsSampler);
    }

    private void sample(Sampler sampler) throws IOException, Resources.ResourceIOException {
        VariabilityDataset dataset = Resources.Instance().load(VariabilityDataset.class, this.config.getVevosGroundTruthDatasetPath());
        VariabilityHistory history = dataset.getVariabilityHistory(new LongestNonOverlappingSequences());
        SPLRepository splRepository = new SPLRepository(config.getVevosSplRepositoryBasePath());
        NonEmptyList<SPLCommit> subhistory = history.commitSequences().iterator().next();
        SPLCommit splCommit = subhistory.iterator().next();

        if (history.commitSequences().size() > 1 || subhistory.size() > 1){
            throw new RuntimeException("Repository does not have exactly one commit.");
        }

        Lazy<Optional<IFeatureModel>> loadFeatureModel = splCommit.featureModel();
        Lazy<Optional<Artefact>> loadPresenceConditions = splCommit.presenceConditionsFallback();
        Artefact pcs = loadPresenceConditions.run().orElseThrow();
        IFeatureModel featureModel = loadFeatureModel.run().orElseThrow();
        Sample variants = sampler.sample(featureModel);
        ArtefactFilter<SourceCodeFile> artefactFilter = ArtefactFilter.KeepAll();
        VariantGenerationOptions generationOptions = VariantGenerationOptions.ExitOnError(false, artefactFilter);

        try {
            splRepository.checkoutCommit(splCommit);
        } catch (final GitAPIException | IOException e) {
            throw new RuntimeException("Failed to checkout commit " + splCommit.id() + " of "
                    + splRepository.getPath() + "!", e);
        }

        Path configsPath = config.getVariantsDir().resolve("configs");
        Files.createDirectories(configsPath);

        for (Variant variant : variants) {
            this.createVariant(variant, config.getVariantsDir(), pcs, generationOptions, configsPath, config.getVevosSplRepositoryBasePath());
        }

        if (splRepository instanceof BusyboxRepository b) {
            try {
                b.postprocess();
            } catch (final GitAPIException | IOException e) {
                Logger.error("Busybox postprocessing failed, please clean up manually (e.g., git stash, git stash drop) at "
                        + splRepository.getPath(), e);
            }
        }

        // ArgoUML adds "Root" to every configuration
        // However, the experiment accounts for such a feature with it's own, called "BASE"
        if (this.config.getRepositoryName().equals("argouml-spl")){
            VevosUtils.removeRootFeatureFromConfigFiles(this.config.getVariantsDir());
        }

        List<String> features = List.of(ConfigTransformer.gatherConfigFeatures(this.config.getVariantsDir().resolve("configs"), this.config.getMaxVariantFeatures()));
        eccoSampleExperimentPreparation(this.config.getVariantsDir(), features);
    }

    /**
     *
     * @param variantConfigurations List of List of feature-names. Each List of feature-names represents the
     *                              configuration of a variant to be created.
     */
    public void createSampleVariants(Path vevosGroundTruthBasePath, String repository, Path sampleBasePath,
                                     List<List<String>> variantConfigurations) throws Resources.ResourceIOException, IOException {
        Path vevosGroundTruthPath = vevosGroundTruthBasePath.resolve(repository);
        Path vevosSplRepositoryPath = vevosGroundTruthBasePath.resolve("REPOS").resolve(repository);
        Path configsPath = sampleBasePath.resolve("configs");

        VariabilityDataset dataset = Resources.Instance().load(VariabilityDataset.class, vevosGroundTruthPath);
        VariabilityHistory history = dataset.getVariabilityHistory(new LongestNonOverlappingSequences());
        NonEmptyList<SPLCommit> subhistory = history.commitSequences().iterator().next();
        SPLCommit splCommit = subhistory.iterator().next();
        Lazy<Optional<Artefact>> loadPresenceConditions = splCommit.presenceConditionsFallback();
        Artefact pcs = loadPresenceConditions.run().orElseThrow();
        NumericNameGenerator nameGenerator = new NumericNameGenerator("Variant");
        ArtefactFilter<SourceCodeFile> artefactFilter = ArtefactFilter.KeepAll();
        VariantGenerationOptions generationOptions = VariantGenerationOptions.ExitOnError(false, artefactFilter);
        final AtomicInteger variantNo = new AtomicInteger();

        Files.createDirectories(configsPath);

        for (List<String> featureNames : variantConfigurations){
            Variant variant = new Variant(nameGenerator.getNameAtIndex(variantNo.getAndIncrement()), new SimpleConfiguration(featureNames));
            this.createVariant(variant, sampleBasePath, pcs, generationOptions, configsPath, vevosSplRepositoryPath);
        }

        Set<String> features = new HashSet<>();
        variantConfigurations.forEach(features::addAll);
        this.eccoSampleExperimentPreparation(sampleBasePath, features.stream().toList());
    }

    private void createVariant(Variant variant, Path sampleBasePath, Artefact pcs, VariantGenerationOptions generationOptions, Path configsPath, Path vevosSplRepositoryPath) throws Resources.ResourceIOException, IOException {
        Path variantPath = sampleBasePath.resolve(variant.getName());
        CaseSensitivePath caseSensitiveVariantDir = CaseSensitivePath.of(variantPath.toString());
        CaseSensitivePath caseSensitiveSplRepositoryPath = CaseSensitivePath.of(vevosSplRepositoryPath.toString());

        Result<GroundTruth, Exception> result = pcs.generateVariant(variant,
                caseSensitiveSplRepositoryPath, caseSensitiveVariantDir, generationOptions);

        if (!result.isSuccess()) {
            throw new RuntimeException("Error upon generation of variant " + variant.getName());
        }

        GroundTruth groundTruth = result.getSuccess();
        Artefact presenceConditionsOfVariant = groundTruth.variant();
        Resources.Instance().write(Artefact.class, presenceConditionsOfVariant,
                variantPath.resolve("pcs.variant.csv"));

        String configFileName = variant.getName() + ".config";
        Path configFilePath = configsPath.resolve(configFileName);
        File configFile = new File(configFilePath.toUri());
        FileWriter fileWriter = new FileWriter(configFile);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.print(variant.getConfiguration().toString());
        printWriter.close();
    }

    private void eccoSampleExperimentPreparation(Path sampleBasePath, List<String> featuresWithoutBase){
        VevosUtils.sanitizeVevosConfigFiles(sampleBasePath);
        ConfigTransformer.transformConfigurations(sampleBasePath);
        VevosUtils.sanitizeVevosFiles(sampleBasePath, featuresWithoutBase);
    }

    public void cleanUp(){
        DirUtils.deleteDir(this.config.getVariantsDir());
        DirUtils.createDir(this.config.getVariantsDir());
    }
}

