package at.jku.isse.ecco.experiment.trainer;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;

import at.jku.isse.ecco.experiment.utils.ServiceUtils;

import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.tinylog.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public class EccoRepoTrainer implements EccoTrainerInterface {
    private final Path repositoryPath;
    private final List<Path> variantPicks;
    private EccoService eccoService;

    public EccoRepoTrainer(ExperimentRunConfiguration config) throws ResourceException {
        this.repositoryPath = Paths.get(ResourceUtils.getResourceFolderPathAsString("repo"));
        this.variantPicks = config.getVariantPicks();
    }

    @Override
    public void train(){
        try{
            Logger.info("Creating ECCO repository...");
            Files.createDirectories(this.repositoryPath);
            this.eccoService = ServiceUtils.createEccoService(this.repositoryPath);

            Logger.info("Committing picked variants...");
            this.commitVariantPicks();
        } catch (Exception e){
            this.cleanUp();
            throw new RuntimeException(e);
        }
    }

    private void commitVariantPicks(){
        int n = 0;
        for (Path variantPath : this.variantPicks){
            n++;
            Logger.info(String.format("Committing variant %d of %d: %s", n, this.variantPicks.size(), variantPath.toString()));
            this.commitVariant(variantPath);
        }
    }

    public void commitVariant(Path variantPath){
        this.eccoService.setBaseDir(variantPath.toAbsolutePath());
        this.eccoService.commit();
    }

    @Override
    public Repository.Op getRepository(){
        return (Repository.Op) this.eccoService.getRepository();
    }

    @Override
    public void cleanUp() {
        if (this.eccoService != null){ this.eccoService.close(); }
        try {
            DirectoryUtils.deleteFolderIfItExists(this.repositoryPath.resolve(".ecco").toAbsolutePath());
        } catch (DirectoryException e) {
            throw new RuntimeException(e);
        }
    }
}

