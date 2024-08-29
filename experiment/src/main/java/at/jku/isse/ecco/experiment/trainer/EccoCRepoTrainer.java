package at.jku.isse.ecco.experiment.trainer;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.utils.DirUtils;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;

import at.jku.isse.ecco.experiment.utils.ServiceUtils;

import org.tinylog.Logger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public class EccoCRepoTrainer implements EccoTrainer {
    private final Path repositoryPath;
    private final List<Path> variantPicks;
    private EccoService eccoService;

    public EccoCRepoTrainer(ExperimentRunConfiguration config){
        this.repositoryPath = Paths.get(ResourceUtils.getResourceFolderPathAsString("repo"));
        this.variantPicks = config.getVariantPicks();
    }

    @Override
    public void train(){
        try{
            Logger.info("Creating ECCO repository...");
            DirUtils.createDir(this.repositoryPath);
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
        DirUtils.deleteDir(this.repositoryPath.resolve(".ecco").toAbsolutePath());
    }
}

