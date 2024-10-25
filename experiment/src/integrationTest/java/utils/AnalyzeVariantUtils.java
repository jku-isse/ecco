package utils;

import at.jku.isse.ecco.featuretrace.parser.VevosConditionHandler;

import java.nio.file.Path;

public class AnalyzeVariantUtils {

    public static int getNumberOfConditionedCodeLines(Path variantPath){
        VevosConditionHandler vevosHandler = new VevosConditionHandler(variantPath);
        return vevosHandler.getNumberOfConditionedCodeLines();
    }
}
