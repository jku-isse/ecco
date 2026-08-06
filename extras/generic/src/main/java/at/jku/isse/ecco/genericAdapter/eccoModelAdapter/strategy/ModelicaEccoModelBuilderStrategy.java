package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder.BuilderArtifactData;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Michael Jahn
 */
public class ModelicaEccoModelBuilderStrategy implements EccoModelBuilderStrategy {

    // Token definition name constants
    private static final String NUM_TOKEN = "NUM";
    private static final String STRING_TOKEN = "STRING";
    private static final String BOOLEAN_TOKEN = "BOOLEAN";
    private static final String NAME_TOKEN = "NAME";

    /**
     * @return a string describing this strategy
     */
    @Override
    public String getStrategyName() {
        return "modelica";
    }

    @Override
    public String getFileExtension() {
        return ".mo";
    }

    /**
     * Helper method to store the tokenDefinitions that correspond to this strategy
     *
     * @return
     */
    @Override
    public List<TokenDefinition> getTokenDefinitions() {
        return Arrays.asList(
                new TokenDefinition(NUM_TOKEN, "(-)?(\\d)+((\\.\\d*E\\d)|(\\.\\d+))?", 10),
                new TokenDefinition(STRING_TOKEN, "\"[^\"]*\"", 11),
                new TokenDefinition(BOOLEAN_TOKEN, "(true)|(false)", 2),
                new TokenDefinition(NAME_TOKEN, "\\w+(\\.\\w+)+", 8)
        );
    }

    /**
     * Helper method to store the sampleSeperator that correpsond to this strategy
     *
     * @return
     */
    @Override
    public List<String> getSampleSeparator() {
        return Arrays.asList(";");
    }

    /**
     * @return true if, all samples must stop on a linebreak
     */
    @Override
    public boolean samplesStopOnLineBreak() {
        return false;
    }

    /**
     * Helper method to store the blockDefinitions that correspond to this strategy
     *
     * @return
     */
    @Override
    public List<BlockDefinition> getBlockDefinitions() {
        return Arrays.asList(
                new BlockDefinition("class", 9, "^\\s*(?:encapsulated )?(?:partial )?class (\\S*)", "\\s*end (\\S*);", true),
                new BlockDefinition("model", 10, "\\s*(?:encapsulated )?(?:partial )?model (\\S*)(\"\\S*\")?", "\\s*end (\\S*);", true),
                new BlockDefinition("record", 9, "^\\s*(?:encapsulated )?(?:partial )?record (\\S*)", "\\s*end (\\S*);", true),
                new BlockDefinition("block", 9, "^\\s*(?:encapsulated )?(?:partial )?block (\\S*)", "\\s*end (\\S*);", true),
//            new BlockDefinition("type", 10, "^\\s*(?:encapsulated )?(?:partial )?type .*", "^\\s*end (\\S*);", true),
                new BlockDefinition("function", 9, "^\\s*(?:encapsulated )?(?:partial )?function (\\S*)", "\\s*end (\\S*);", true),
                new BlockDefinition("encapfunction", 9, "^\\s*encapsulated function (\\S*)", "\\s*end (\\S*);", true),
                new BlockDefinition("operator", 9, "^\\s*(?:encapsulated )?(?:partial )?operator (\\S*)", "\\s*end (\\S*);", true),
                new BlockDefinition("package", 9, "^\\s*(?:encapsulated )?(?:partial )?package (\\S*)", "\\s*end (\\S*);", true),
                new BlockDefinition("when", 9, "^\\s*when .*", "\\s*end when;", false),
                new BlockDefinition("for", 9, "^\\s*for .*", "\\s*end for;", false),
                new BlockDefinition("if", 9, "^\\s*if .*", "\\s*end if;", false),
                new BlockDefinition("while", 9, "^\\s*while .*", "\\s*end while;", false),
                new BlockDefinition("initial equation", 10, "\\s*initial equation", null, false),
                new BlockDefinition("equation", 10, "\\s*equation", null, false),
//                new BlockDefinition("inlined connector", 10, "^\\s*connector (\\S*) =", ";",false),
                new BlockDefinition("connector", 5, "^\\s*(?:encapsulated )?(?:partial )?(?:expandable )?connector (\\S*)", "\\s*end (\\S*);", true)
        );
    }

    /**
     * Helper method to store the comment definitions
     *
     * @return
     */
    @Override
    public List<String> getCommentBlockDefinitions() {
        return Arrays.asList("/\\*([^\\*/]*)\\*/");
    }

    /**
     * @param parsedNonTerminals
     * @param parsedTokenValues
     * @return the built artifact, with custom identifier and type
     */
    @Override
    public BuilderArtifactData createArtifactData(List<String> parsedNonTerminals, List<TokenValue> parsedTokenValues) {
        List<String> valueString = new ArrayList<>();
        StringBuilder alphanumericKeywords = new StringBuilder();
        List<String> printValues = new ArrayList<>();

        for (TokenValue tokenValue : parsedTokenValues) {
            printValues.add(tokenValue.getValue());
            if (tokenValue.isUndefinedToken() && tokenValue.getValue().length() > 1) {
                alphanumericKeywords.append(tokenValue.getValue());
            } else if (!tokenValue.isUndefinedToken()) {
                valueString.add(tokenValue.getValue());
            }
        }

        return new BuilderArtifactData(valueString, alphanumericKeywords.toString(), parsedTokenValues, printValues);
    }

    /**
     * Returns an object that identifies the given artifact to be used in references
     * Can return null, if no references across artifacts shall be supported
     *
     * @param artifact
     * @return
     */
    @Override
    public Object getArtifactReferenceId(BuilderArtifactData artifact) {
        return null;
    }

    /**
     * Returns a list of object with ids that are references by this artifact
     * Can return null, or an empty list no references across artifacts shall be supported
     *
     * @param artifact
     * @return
     */
    @Override
    public List<Object> getArtifactReferencingIds(BuilderArtifactData artifact) {
        return null;
    }

    /**
     * @return a newly generated artifact reference Id, may return null if no references
     * are supported, or all references are already set in the {@link EccoModelBuilderStrategy#getArtifactReferenceId(BuilderArtifactData)}
     */
    @Override
    public Object getNextArtifactReferenceId() {
        return null;
    }

    /**
     * resets the artifact reference id
     */
    @Override
    public void resetNextArtifactReferenceId() {

    }

    /**
     * Include the possibility to post process the whole ecco model.
     * May return null, or the same nodeSet as input if no post processing is necessary
     *
     * @param inputRootNode
     * @return
     */
    @Override
    public Node postProcessing(Node inputRootNode) {
        return inputRootNode;
    }

    /**
     * @return true, if all ecco child nodes should be ordered nodes, i.e. that the order of the artifacts is important
     * @param artifact
     */
    @Override
    public boolean useOrderedNode(BuilderArtifactData artifact) {
        return true;
    }

    /**
     * @return if, the "uses" references should be taken into account when comparing two artifacts with the equals method
     */
    @Override
    public boolean useReferencesInEquals() {
        return false;
    }
}
