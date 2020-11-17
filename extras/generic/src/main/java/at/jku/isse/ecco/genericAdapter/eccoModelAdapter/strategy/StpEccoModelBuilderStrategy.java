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
public class StpEccoModelBuilderStrategy implements EccoModelBuilderStrategy {

    // Token definition name constants
    private static final String REF_TOKEN = "REF";
    private static final String COMMENT_TOKEN = "COMMENT";
    private static final String NUM_TOKEN = "NUM";
    private static final String STRING_TOKEN = "STRING";
    private static final String BOOLEAN_TOKEN = "BOOLEAN";
    private static final String HEADER_BEGIN_TOKEN = "HEADER";
    private static final String DATA_BEGIN_TOKEN = "DATA";
    private static final String ISO_BEGIN_TOKEN = "ISO";

    // last returned referencing id
    private static int lastRefId = 1;

    /**
     * @return a string describing this strategy
     */
    @Override
    public String getStrategyName() {
        return "stp";
    }

    @Override
    public String getFileExtension() {
        return ".stp";
    }

    @Override
    public List<TokenDefinition> getTokenDefinitions() {
        return Arrays.asList(
                new TokenDefinition(COMMENT_TOKEN, "/\\*[\\s\\S]*\\*/", 4),
                new TokenDefinition(NUM_TOKEN, "(?<!\\w)(-)?(\\d)+(\\.(\\d*(E(-|\\+)?(\\d)+)?)?)?", 5),
                new TokenDefinition(STRING_TOKEN, "'[^']*'", 11),
                new TokenDefinition(REF_TOKEN, "#(\\d)*", 12),
                new TokenDefinition(BOOLEAN_TOKEN, "\\.(#T|F)\\.", 10)
        );
    }

    @Override
    public List<String> getSampleSeparator() {
        return Arrays.asList(";");
    }

    /**
     * @return true if, all samples must stop on a linebreak
     */
    @Override
    public boolean samplesStopOnLineBreak() {
        return true;
    }

    @Override
    public List<BlockDefinition> getBlockDefinitions() {
        return Arrays.asList(
                new BlockDefinition(HEADER_BEGIN_TOKEN.toLowerCase(), 10, "HEADER;", "ENDSEC;", false),
                new BlockDefinition(DATA_BEGIN_TOKEN.toLowerCase(), 10, "DATA;", "ENDSEC;", false),
                new BlockDefinition(ISO_BEGIN_TOKEN.toLowerCase(), 10, "^ISO-10303-21;", "END-ISO-10303-21;", false)
        );
    }

    @Override
    public List<String> getCommentBlockDefinitions() {
        return Arrays.asList("/\\*[\\s\\S]*\\*/");
    }

    /**
     * @param parsedNonTerminals
     * @param parsedTokenValues
     * @return the built artifact, with custom identifier and type
     */
    @Override
    public BuilderArtifactData createArtifactData(List<String> parsedNonTerminals, List<TokenValue> parsedTokenValues) {
        List<String> identifiers = new ArrayList<>();
        StringBuilder alphanumericKeywords = new StringBuilder();
        List<String> printValues = new ArrayList<>();

        for (TokenValue tokenValue : parsedTokenValues) {
            if(tokenValue.isUndefinedToken() && tokenValue.getValue().length() > 1) {
                alphanumericKeywords.append(tokenValue.getValue());
                printValues.add(tokenValue.getValue());
            } else if(!tokenValue.isUndefinedToken() && tokenValue.getTokenDefinition() != null) {
                if(REF_TOKEN.equals(tokenValue.getTokenDefinition().getName())) {
                    printValues.add(tokenValue.getValue().substring(0,1));
                    if(parsedTokenValues.indexOf(tokenValue) == 0) {
                        printValues.add(BuilderArtifactData.RESOLVE_OWN_REF_ID);
                    } else {
                        printValues.add(BuilderArtifactData.RESOLVE_USES_REF_ID);
                    }
                } else {
                    identifiers.add(tokenValue.getValue());
                    printValues.add(tokenValue.getValue());
                }
            } else {
                printValues.add(tokenValue.getValue());
            }
        }

        String type = alphanumericKeywords.toString();
        if (type.isEmpty() && parsedNonTerminals.size() > 0) {
            type = parsedNonTerminals.get(0).toUpperCase();
        }
        if (identifiers.isEmpty() && parsedNonTerminals.size() > 0) {
            identifiers.add(parsedNonTerminals.get(0).toUpperCase());
        }

        return new BuilderArtifactData(identifiers, type, parsedTokenValues, printValues);
    }


    @Override
    public Object getArtifactReferenceId(BuilderArtifactData artifact) {

        // referencing REF must always be the first token in stp
        TokenValue firstToken = artifact.getParsedTokenValues().size() > 0 ? artifact.getParsedTokenValues().get(0) : null;
        if (firstToken != null && firstToken.getTokenDefinition() != null && REF_TOKEN.equals(firstToken.getTokenDefinition().getName())) {
//            artifact.setRefId(firstToken.getValue());
            return firstToken.getValue();
        }
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

        List<TokenValue> parsedTokenValues = artifact.getParsedTokenValues();
        List<Object> artifactReferences = new ArrayList<>();

        for (int i = 1; i < parsedTokenValues.size(); i++) {
            if (parsedTokenValues.get(i).getTokenDefinition().getName().equals(REF_TOKEN)) {
                artifactReferences.add(parsedTokenValues.get(i).getValue());
            }
        }
        return artifactReferences;
    }

    /**
     * @return a newly generated artifact reference Id, may return null if no references
     * are supported, or all references are already set in the {@link EccoModelBuilderStrategy#getArtifactReferenceId(BuilderArtifactData)}
     */
    @Override
    public Object getNextArtifactReferenceId() {
        return lastRefId++;
    }

    /**
     * resets the artifact reference id
     */
    @Override
    public void resetNextArtifactReferenceId() {
        lastRefId = 1;
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
     * @param artifact
     * @return true, if all ecco child nodes should be ordered nodes, i.e. that the order of the artifacts is important
     */
    @Override
    public boolean useOrderedNode(BuilderArtifactData artifact) {
        if(artifact.getType().matches("S\\d+")) {
            return true;
        }
        return false;
    }

    /**
     * @return if, the "uses" references should be taken into account when comparing two artifacts with the equals method
     */
    @Override
    public boolean useReferencesInEquals() {
        return true;
    }
}
