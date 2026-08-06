package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

/**
 * Statistics Parameters
 *
 * @author Michael Jahn
 */
public class Statistics {
    /**
     *
     */
    public static int nrEnhancedFallbackMutation = 0;
    public static int successFullEnhancedFallbackMutations = 0;
    public static int nrFailedSanityCheck = 0;
    public static int nrGeneralFallbackMutation = 0;
    public static int successGeneralFallbackMutation = 0;


    public static String printStatistics() {
        return "------------------------------------STATISTICS------------------------------------" +
                "\nnrGeneralFallbackMutation: " + nrGeneralFallbackMutation +
                "\nsuccessGeneralFallbackMutation: " + successGeneralFallbackMutation +
                "\nnrEnhancedFallbackMutations: " + nrEnhancedFallbackMutation +
                "\nnrSuccessfullEnhancedFallbackMutations: " + successFullEnhancedFallbackMutations +
                "\nnrFailedDueToSanityCheck: " + nrFailedSanityCheck +
                "\n---------------------------------------------------------------------------------";
    }
}
