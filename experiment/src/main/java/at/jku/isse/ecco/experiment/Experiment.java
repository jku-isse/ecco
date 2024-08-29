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


public class Experiment {

    public static void main(String[] args) throws ResourceIOException, IOException, URISyntaxException {
        String configPath = ResourceUtils.getResourceFolderPathAsString("configuration/experiment.properties");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath);
        runExperimentSet(experimentConfig);
    }

    public static void runExperimentSet(ExperimentConfiguration experimentConfig){
        Logger.info("Number of runs left to do: " + experimentConfig.getNumberOfRunsLeft());
        Logger.info("Running experiment with the following configuration:\n" + experimentConfig.toString());
        ExperimentRunConfiguration config = experimentConfig.getNextRunConfiguration();
        while(config != null){
            runExperiments(config);
            config = experimentConfig.getNextRunConfiguration();
        }
    }

    public static void runExperiments(ExperimentRunConfiguration config) {
        Logger.info("Running the following configuration:\n" + config.toString());
        for (int i = 1; i <= config.getNumberOfRuns(); i++) {
            VevosFeatureSampler sampler = new VevosFeatureSampler(config);
            EccoTrainer trainer = null;
            try {
                sampler.sample();
                config.pickVariants();
                trainer = new EccoCRepoTrainer(config);
                trainer.train();
                String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
                ResultPersister persister = new ResultDatabasePersister(databasePath);
                ExperimentRunner runner = new CExperimentRunner(config, trainer.getRepository(), persister);
                runner.runExperiment();
            } catch (Exception e) {
                e.printStackTrace();
                // throw new RuntimeException(e.getMessage());
            } finally {
                if (trainer != null) {
                    trainer.cleanUp();
                }
                sampler.cleanUp();
            }
        }
    }



}
