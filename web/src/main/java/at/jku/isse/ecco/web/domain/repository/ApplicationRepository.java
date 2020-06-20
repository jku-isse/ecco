package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.web.controller.ApplicationController;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationRepository extends AbstractRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRepository.class);

    private EccoApplication eccoApplication;

    public ApplicationRepository() {

    }

    public ApplicationRepository(EccoApplication eccoApplication) {
        this.eccoApplication = eccoApplication;
    }

    public boolean initializeRepository(String repositoryDirectory) {
        try {
            this.eccoApplication.init(repositoryDirectory);
            return true;
        } catch (EccoException eccoException) {
            LOGGER.error(eccoException.getMessage());
            eccoException.printStackTrace();
            return false;
        }
    }
}
