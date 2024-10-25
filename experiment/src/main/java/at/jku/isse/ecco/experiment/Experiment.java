package at.jku.isse.ecco.experiment;

import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.persister.ResultDatabasePersister;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.experiment.runner.CExperimentRunner;
import at.jku.isse.ecco.experiment.runner.ExperimentRunner;
import at.jku.isse.ecco.experiment.sample.VevosFeatureSampler;
import at.jku.isse.ecco.experiment.trainer.EccoTrainer;
import at.jku.isse.ecco.experiment.trainer.EccoCRepoTrainer;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import org.variantsync.vevos.simulation.io.Resources.ResourceIOException;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;


public class Experiment {

    private final boolean sample;
    private final Path variantBasePath;

    public Experiment(boolean sample, Path variantBasePath){
        this.sample = sample;
        this.variantBasePath = variantBasePath;
    }

    public static void main(String[] args) throws ResourceIOException, IOException, URISyntaxException {
        String configPath = ResourceUtils.getResourceFolderPathAsString("configuration/experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("sample");
        Experiment experiment = new Experiment(true, variantBasePath);
        experiment.runExperiment(configPath);
    }

    public void runExperiment(String configPath){
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, this.variantBasePath);
        this.executeExperimentConfig(experimentConfig);
    }

    private void executeExperimentConfig(ExperimentConfiguration experimentConfig){
        Logger.info("Running experiment set with the following configuration:\n" + experimentConfig.toString());
        ExperimentRunConfiguration config = experimentConfig.getNextRunConfiguration();
        while(config != null){
            Logger.info("Number of experiments left to do: " + experimentConfig.getNumberOfRunsLeft());
            executeExperimentRunConfig(config);
            config = experimentConfig.getNextRunConfiguration();
        }
    }

    private void executeExperimentRunConfig(ExperimentRunConfiguration config) {
        Logger.info("Running the following experiment:\n" + config.toString());
        for (int i = 1; i <= config.getNumberOfRuns(); i++) {
            VevosFeatureSampler sampler = new VevosFeatureSampler(config);
            EccoTrainer trainer = null;
            try {
                if (this.sample) {
                    sampler.sample();
                }
                config.pickVariants();
                trainer = new EccoCRepoTrainer(config);
                trainer.train();
                String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
                ResultPersister persister = new ResultDatabasePersister(databasePath);
                ExperimentRunner runner = new CExperimentRunner(config, trainer.getRepository(), persister);
                runner.runExperiment();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            } finally {
                if (trainer != null) {
                    trainer.cleanUp();
                }
                sampler.cleanUp();
            }
        }
    }
}
