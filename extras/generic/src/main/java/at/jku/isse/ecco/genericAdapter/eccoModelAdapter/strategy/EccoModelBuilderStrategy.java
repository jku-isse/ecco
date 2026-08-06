package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder.BuilderArtifactData;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;
import at.jku.isse.ecco.tree.Node;

import java.util.List;

/**
 * Strategy interface with all methods required for a specific file type
 *
 * @author Michael Jahn
 */
public interface EccoModelBuilderStrategy {

    /**
     * @return a string describing this strategy
     */
    String getStrategyName();

    String getFileExtension();


    /**
     * Helper method to store the tokenDefinitions that correspond to this strategy
     *
     * @return
     */
    List<TokenDefinition> getTokenDefinitions();

    /**
     * Helper method to store the sampleSeperator that correpsond to this strategy
     *
     * @return
     */
    List<String> getSampleSeparator();

    /**
     * @return true if, all samples must stop on a linebreak
     */
    boolean samplesStopOnLineBreak();

    /**
     * Helper method to store the blockDefinitions that correspond to this strategy
     *
     * @return
     */
    List<BlockDefinition> getBlockDefinitions();

    /**
     * Helper method to store the comment definitions, comments
     * will be completely ignored by the inference process
     *
     * @return
     */
    List<String> getCommentBlockDefinitions();

    /**
     * @param parsedNonTerminals .
     * @param parsedTokenValues .
     * @return the built artifact, with custom identifier and type
     */
    BuilderArtifactData createArtifactData(List<String> parsedNonTerminals, List<TokenValue> parsedTokenValues);

    /**
     * Returns an object that identifies the given artifact to be used in references
     * Can return null, if no references across artifacts shall be supported.
     * May set the refId of the given {@link BuilderArtifactData} then the original
     * refId will be used for printing, otherwise a new one will be generated,
     * using {@link #getNextArtifactReferenceId()}
     *
     * @param artifactData .
     * @return the reference id
     */
    Object getArtifactReferenceId(BuilderArtifactData artifactData);


    /**
     * Returns a list of object with ids that are references by this artifact
     * Can return null, or an empty list no references across artifacts shall be supported
     *
     * @param artifact .
     * @return the referenced ids
     */
    List<Object> getArtifactReferencingIds(BuilderArtifactData artifact);

    /**
     * @return a newly generated artifact reference Id, may return null if no references
     * are supported, or all references are already set in the {@link #getArtifactReferenceId(BuilderArtifactData)}
     */
    Object getNextArtifactReferenceId();

    /**
     * resets the artifact reference id
     */
    void resetNextArtifactReferenceId();

    /**
     * Include the possibility to post process the whole ecco model.
     * May return null, or the same nodeSet as input if no post processing is necessary
     *
     * @param inputRootNode .
     * @return the post processed node set
     */
    Node postProcessing(Node inputRootNode);

    /**
     *
     * @return true, if all ecco child nodes should be ordered nodes, i.e. that the order of the artifacts is important
     * @param artifact, the artifact for which an ordered node should be used
     */
    boolean useOrderedNode(BuilderArtifactData artifact);

    /**
     * @return if, the "uses" references should be taken into account when comparing two artifacts with the equals method
     */
    boolean useReferencesInEquals();

}
