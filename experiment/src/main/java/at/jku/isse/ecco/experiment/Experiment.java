package at.jku.isse.ecco.experiment;

import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.picker.featuretracepicker.RandomFeatureTracePicker;
import at.jku.isse.ecco.experiment.result.persister.ResultDatabasePersister;
import at.jku.isse.ecco.experiment.result.persister.ResultInMemoryPersister;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.experiment.runner.ExperimentRunner;
import at.jku.isse.ecco.experiment.runner.ExperimentRunnerInterface;
import at.jku.isse.ecco.experiment.sample.VevosFeatureSampler;
import at.jku.isse.ecco.experiment.trainer.EccoTrainerInterface;
import at.jku.isse.ecco.experiment.trainer.EccoRepoTrainer;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.variantsync.vevos.simulation.io.Resources.ResourceIOException;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;


public class Experiment {

    private final boolean sample;
    private final ResultPersister resultPersister;

    public Experiment(boolean sample, ResultPersister resultPersister){
        this.sample = sample;
        this.resultPersister = resultPersister;
    }

    public static void main(String[] args) throws ResourceIOException, IOException, URISyntaxException, ResourceException {
        /*String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
        ResultPersister persister = new ResultDatabasePersister(databasePath);
        Experiment experiment = new Experiment(false, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configuration/experiment.properties");
        Path variantBasePath = Paths.get("C:\\Users\\Berni\\Desktop\\Project\\ecco\\experiment\\src\\integrationTest\\resources\\strange_sample");

        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);*/



        /*ResultInMemoryPersister persister = new ResultInMemoryPersister();
        Experiment experiment = new Experiment(true, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configuration/experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("sample");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);
        System.out.println();*/



        String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
        ResultPersister persister = new ResultDatabasePersister(databasePath);
        Experiment experiment = new Experiment(true, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configuration/experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("sample");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);
    }

    public void runExperiment(ExperimentConfiguration experimentConfig){
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
            VevosFeatureSampler sampler = new VevosFeatureSampler();
            EccoTrainerInterface trainer = null;
            try {
                if (this.sample) {
                    sampler.sample(config, 50);
                }
                config.pickVariants();
                trainer = new EccoRepoTrainer(config);
                trainer.train();
                ExperimentRunnerInterface runner = new ExperimentRunner(config, trainer.getRepository(), this.resultPersister);
                runner.runExperiment();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            } finally {
                if (trainer != null) {
                    trainer.cleanUp();
                }
                if (this.sample) {
                    sampler.cleanUp();
                }
            }
        }
    }
}
