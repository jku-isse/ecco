package at.jku.isse.ecco.experiment.trainer;

import at.jku.isse.ecco.repository.Repository;

public interface EccoTrainerInterface {
    void train();
    void cleanUp();
    Repository.Op getRepository();
}
