package at.jku.isse.ecco.experiment.utils;

import at.jku.isse.ecco.service.EccoService;

import java.nio.file.Path;

public class ServiceUtils {
    public static EccoService createEccoService(Path repositoryPath){
        EccoService eccoService = new EccoService();
        eccoService.setRepositoryDir(repositoryPath.resolve(".ecco").toAbsolutePath());
        eccoService.init();
        return eccoService;
    }

    public static EccoService openRepositoryAndGetEccoService(Path repositoryPath){
        EccoService eccoService = new EccoService();
        eccoService.setRepositoryDir(repositoryPath.resolve(".ecco").toAbsolutePath());
        eccoService.open();
        return eccoService;
    }
}
