package at.jku.isse.ecco.experiment.result.persister;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class ResultFilePersister implements ResultPersister {

    private final Path filePath;

    public ResultFilePersister(Path filePath){
        this.filePath = filePath;
    }

    @Override
    public void persist(Result result, ExperimentRunConfiguration config) {
        String resultStart = "TP; FP; TN; FN; Precision; Recall; F1\n";
        String resultEnd = String.format("%d, %d, %d, %d, %f, %f, %f",
                result.getTp(), result.getFp(), result.getTn(), result.getFn(), result.getPrecision(), result.getRecall(), result.getF1());
        String resultString = resultStart + resultEnd;
        byte[] strToBytes = resultString.getBytes();
        try {
            Files.write(this.filePath, strToBytes);
        } catch (IOException e){
            throw new RuntimeException("Could not write result: " + e.getMessage());
        }
    }
}
