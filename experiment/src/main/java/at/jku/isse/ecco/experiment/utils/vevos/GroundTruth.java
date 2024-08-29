package at.jku.isse.ecco.experiment.utils.vevos;

import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.featuretrace.parser.VevosCondition;
import at.jku.isse.ecco.featuretrace.parser.VevosConditionHandler;
import at.jku.isse.ecco.featuretrace.parser.VevosFileConditionContainer;
import at.jku.isse.ecco.util.Location;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroundTruth {

    private Path groundTruthsPath;

    // configuration-string to handler
    // no revisions, alphabetically sorted, comma-separated
    private Map<String, VevosConditionHandler> handlerMap = new HashMap<>();

    public GroundTruth(Path groundTruthsPath){
        this.groundTruthsPath = groundTruthsPath;
        this.parseGroundTruthFiles();
    }

    private void parseGroundTruthFiles(){
        List<Path> variantPaths = VevosUtils.getVariantFolders(this.groundTruthsPath);
        for (Path variantPath : variantPaths){
            VevosConditionHandler conditionHandler = new VevosConditionHandler(variantPath);
            String configurationString = VevosUtils.variantPathToConfigString(variantPath);
            this.handlerMap.put(configurationString, conditionHandler);
        }
    }

    public Formula getCondition(Location location, FormulaFactory formulaFactory){
        VevosConditionHandler handler = this.handlerMap.get(location.getConfigurationString());
        VevosFileConditionContainer conditionContainer = handler.getFileSpecificPresenceConditions(location.getFilePath().toAbsolutePath());
        Collection<VevosCondition> conditions = conditionContainer.getMatchingPresenceConditions(location.getStartLine(), location.getEndLine());
        if (conditions.size() == 0){
            return formulaFactory.constant(true);
        } else if (conditions.size() > 1){
            return conditions.stream()
                    .map(vc -> LogicUtils.parseString(formulaFactory, vc.getConditionString()))
                    .reduce(formulaFactory::and).get();
        } else {
            VevosCondition condition = conditions.iterator().next();
            return LogicUtils.parseString(formulaFactory, condition.getConditionString());
        }
    }
}
