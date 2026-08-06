package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;

import java.io.IOException;
import java.util.List;

/**
 * Main Interface for the grammar inference module
 *
 * @author Michael Jahn
 */
public interface GrammarInferenceFacade {


    /**
     * Runs the whole grammar inference pipeline on the given {@link List<String>filePaths} and returns the resulting {@link NonTerminal rootSymbol}
     *
     * @param filePaths, list of file paths to be used as input files
     * @param strategy
     * @param grammarDataOutputPath
     * @return {@link NonTerminal} or null if an error occured during inference
     * @throws IOException
     * @throws AmbiguousTokenDefinitionsException, in case the tokenDefinitions are overlapping (can be resolved by assigning different priorities to {@link TokenDefinition}
     */
    NonTerminal inferGrammar(List<String> filePaths, EccoModelBuilderStrategy strategy, String grammarDataOutputPath) throws IOException, AmbiguousTokenDefinitionsException;

    /**
     * Updates the given grammar to also accept the given {@link List<String>filePaths} and writes the updated grammar to the given grammarDataFile
     *
     * @param filePaths
     * @param strategy
     * @return
     */
    NonTerminal updateGrammar(List<String> filePaths, EccoModelBuilderStrategy strategy, String grammarDataFile) throws IOException, AmbiguousTokenDefinitionsException;

}
