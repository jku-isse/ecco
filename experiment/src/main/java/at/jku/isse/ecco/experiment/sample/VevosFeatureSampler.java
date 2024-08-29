package at.jku.isse.ecco.experiment.sample;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.utils.DirUtils;
import at.jku.isse.ecco.experiment.utils.FeatureUtils;
import at.jku.isse.ecco.experiment.utils.vevos.ConfigTransformer;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tinylog.Logger;
import org.variantsync.functjonal.Lazy;
import org.variantsync.functjonal.Result;
import org.variantsync.functjonal.list.NonEmptyList;
import org.variantsync.vevos.simulation.VEVOS;
import org.variantsync.vevos.simulation.feature.Variant;
import org.variantsync.vevos.simulation.feature.sampling.Sample;
import org.variantsync.vevos.simulation.feature.sampling.Sampler;
import org.variantsync.vevos.simulation.feature.sampling.SimpleSampler;
import org.variantsync.vevos.simulation.io.Resources;
import org.variantsync.vevos.simulation.repository.BusyboxRepository;
import org.variantsync.vevos.simulation.repository.SPLRepository;
import org.variantsync.vevos.simulation.util.io.CaseSensitivePath;
import org.variantsync.vevos.simulation.variability.EvolutionStep;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class VevosFeatureSampler {

    private final int NUMBER_OF_VARIANTS_TO_GENERATE = 50;
    private final int SAMPLE_ATTEMPTS = 3;
    private final ExperimentRunConfiguration config;

    public VevosFeatureSampler(ExperimentRunConfiguration config){
        this.config = config;
        VEVOS.Initialize();
    }

    public void sample() throws Resources.ResourceIOException, IOException {
        for (int i = 1; i <= this.SAMPLE_ATTEMPTS; i++){
            this.sampleAttempt();
            List<String> features = List.of(ConfigTransformer.gatherConfigFeatures(this.config.getVariantsDir().resolve("configs"), this.config.getMaxVariantFeatures()));
            boolean allValid = features.stream().map(FeatureUtils::featureNameIsValid).reduce(true, (a, b) -> a && b);
            if (allValid){
                return;
            } else {
                Logger.error("VEVOS Sampling failed to sample features with valid names: " + features);
                Logger.info(String.format("Attempting sampling after invalid sample (try number %d)...", i + 1));
                this.cleanUp();
            }
        }
        Logger.error("VEVOS Sampling failed 5 times in a row to sample features with valid names!");
        throw new RuntimeException("VEVOS Sampling failed 5 times in a row to sample features with valid names!");
    }

    public void sampleAttempt() throws Resources.ResourceIOException, IOException {
        VariabilityDataset dataset = Resources.Instance().load(VariabilityDataset.class,
                this.config.getVevosGroundTruthDatasetPath());
        Set<EvolutionStep<SPLCommit>> evolutionSteps = dataset.getEvolutionSteps();

        Logger.info("The dataset contains " + dataset.getSuccessCommits().size()
                + " commits for which the variability extraction succeeded.");
        Logger.info("The dataset contains " + dataset.getErrorCommits().size()
                + " commits for which the variability extraction failed.");
        Logger.info("The dataset contains " + dataset.getEmptyCommits().size()
                + " commits for which there is no ground truth.");
        Logger.info("The dataset contains " + dataset.getPartialSuccessCommits().size()
                + " commits that for which the file presence conditions are missing.");
        Logger.info("The dataset contains " + evolutionSteps.size() + " usable pairs of commits.");

        VariabilityHistory history = dataset.getVariabilityHistory(new LongestNonOverlappingSequences());
        Sampler variantsSampler = SimpleSampler.CreateRandomSampler(NUMBER_OF_VARIANTS_TO_GENERATE, config.getMinVariantFeatures(), config.getMaxVariantFeatures());
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
        Sample variants = variantsSampler.sample(featureModel);
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
            Path variantDir = config.getVariantsDir().resolve(variant.getName());
            CaseSensitivePath caseSensitiveVariantDir = CaseSensitivePath.of(variantDir.toString());
            CaseSensitivePath caseSensitiveSplRepositoryPath = CaseSensitivePath.of(config.getVevosSplRepositoryBasePath().toString());

            Result<GroundTruth, Exception> result = pcs.generateVariant(variant,
                    caseSensitiveSplRepositoryPath, caseSensitiveVariantDir, generationOptions);

            if (!result.isSuccess()) {
                throw new RuntimeException("Error upon generation of variant " + variant.getName()
                                + " at SPL commit " + splCommit.id() + "!", result.getFailure());
            }

            GroundTruth groundTruth = result.getSuccess();
            Artefact presenceConditionsOfVariant = groundTruth.variant();
            Resources.Instance().write(Artefact.class, presenceConditionsOfVariant,
                    variantDir.resolve("pcs.variant.csv"));

            try {
                String configFileName = variant.getName() + ".config";
                Path configFilePath = configsPath.resolve(configFileName);
                File configFile = new File(configFilePath.toUri());
                FileWriter fileWriter = new FileWriter(configFile);
                PrintWriter printWriter = new PrintWriter(fileWriter);
                printWriter.print(variant.getConfiguration().toString());
                printWriter.close();
            } catch (IOException e){
                throw new RuntimeException(e.getMessage());
            }
        }

        if (splRepository instanceof BusyboxRepository b) {
            try {
                b.postprocess();
            } catch (final GitAPIException | IOException e) {
                Logger.error("Busybox postprocessing failed, please clean up manually (e.g., git stash, git stash drop) at "
                        + splRepository.getPath(), e);
            }
        }

        ConfigTransformer.transformConfigurations(this.config.getVariantsDir());
    }

    public void cleanUp(){
        DirUtils.deleteDir(this.config.getVariantsDir());
        DirUtils.createDir(this.config.getVariantsDir());
    }
}

