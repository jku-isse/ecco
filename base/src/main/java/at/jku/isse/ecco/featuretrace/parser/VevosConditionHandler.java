package at.jku.isse.ecco.featuretrace.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.KeyException;
import java.util.*;


/**
 * Contains variant-specific conditions.
 */
public class VevosConditionHandler {

    private final String PRESENCE_CONDITION_FILENAME = "pcs.variant.csv";
    private final Path vevosFilePath;
    private boolean vevosFileExists;
    private Map<Path, List<VevosCondition>> fileConditionsMap = new HashMap<>();

    public VevosConditionHandler(Path vevosFileBasePath){
        this.vevosFilePath = vevosFileBasePath.resolve(this.PRESENCE_CONDITION_FILENAME);
        this.vevosFileExists = Files.exists(vevosFilePath);
        if (this.vevosFileExists){
            this.parsePresenceConditions();
        }
    }

    private void parsePresenceConditions() {
        try {
            List<String> vevosFileLines = Files.readAllLines(this.vevosFilePath);
            // first line in a VEVOS file just showcases structure
            vevosFileLines.remove(0);
            this.parseVevosFileLines(vevosFileLines);
        } catch (IOException e){
            throw new RuntimeException(String.format("VEVOS file (%s) could not be read: %s", this.vevosFilePath,e.getMessage()));
        }
    }

    private void parseVevosFileLines(List<String> vevosFileLines){
        try {
            for (String line : vevosFileLines) {
                // ignore file conditions
                if(line.contains(";True;True;True;")){ continue; }
                this.addConditionToMap(new VevosCondition(line));
            }
        } catch(IllegalArgumentException e){
            throw new RuntimeException(String.format("VEVOS file entries could not be parsed: %s", e.getMessage()));
        }
    }

    private void addConditionToMap(VevosCondition condition){
        List<VevosCondition> conditions = this.fileConditionsMap.get(condition.getFilePath().toAbsolutePath());
        if (conditions == null){
            List<VevosCondition> newConditionList = new LinkedList<>();
            newConditionList.add(condition);
            this.fileConditionsMap.put(condition.getFilePath().toAbsolutePath(), newConditionList);
        } else {
            conditions.add(condition);
        }
    }

    public VevosFileConditionContainer getFileSpecificPresenceConditions(Path filePath){
        List<VevosCondition> conditions = this.fileConditionsMap.get(filePath.toAbsolutePath());
        if (conditions == null){
            conditions = new LinkedList<>();
        }
        return new VevosFileConditionContainer(conditions);
    }
}
