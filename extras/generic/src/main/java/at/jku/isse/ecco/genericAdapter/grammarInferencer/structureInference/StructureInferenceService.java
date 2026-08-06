package at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.Node;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.NonTerminalNode;

import java.io.IOException;
import java.util.List;

/**
 * Facade for the structureInference module
 *
 * @author Michael Jahn
 */
public interface StructureInferenceService {

    /**
     * Builds the basic structure of the given file and induct the basic graph grammar from it
     *
     * @param filePath
     * @return the root symbol of the infered grammar, which are all "structured" rules
     */
    NonTerminalNode inferBaseStructureFromFiles(String filePath, List<BlockDefinition> blockDefinitions) throws IOException;

    /**
     * Builds the basic structure of the given files and return the inducted graph grammar represeting all the given files
     *
     * @param filePath
     * @param blockDefinitions
     * @return
     * @throws IOException
     */
    Node inferBaseStructureFromFiles(List<String> filePath, List<BlockDefinition> blockDefinitions) throws IOException;

    Node inferBaseStructure(List<NonTerminalNode> fileStructures, List<BlockDefinition> blockDefinitions);

    List<NonTerminalNode> inferFileStructures(List<String> filePath, List<BlockDefinition> blockDefinitions) throws IOException;

    NonTerminal inferGraphGrammar(Node baseStructure, List<BlockDefinition> blockDefinitions);

    /**
     * Infers the combined base structure from the given files using the {@link List<BlockDefinition>} blockDefitions
     * and inducts the graph grammar from this structure
     *
     * @param filePaths
     * @param blockDefinitions
     * @return root symbol of the inducted graph grammar
     * @throws IOException
     */
    NonTerminal inferGraphGrammar(List<String> filePaths, List<BlockDefinition> blockDefinitions) throws IOException;


    /**
     * Uses the given blockDefitions to parse the base structure and return a structure graph with all contents set
     *
     * @param filePath
     * @param blockDefinitions
     * @return
     */
    NonTerminalNode parseBaseStructure(String filePath, List<BlockDefinition> blockDefinitions) throws IOException;
}
