package at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Main interface for the grammar inference module
 *
 * @author Michael Jahn
 */
public interface GrammarMutatorService {

    /**
     * -------------------------- Main Methods ------------------------------------
     */

    /**
     * Reads the given file, separates the samples by the given sampleSeparator, tokenizes the samples with the tokenDefinitions
     * and runs the whole grammar inference algorithm
     *
     * @param tokenDefinitions
     * @param sampleSeparator
     * @param filePath
     * @param sampleStopOnLineBreak
     * @return the rootSymbol {@link NonTerminal} of the inferred grammar
     */
    NonTerminal inferGrammar(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, String filePath, boolean sampleStopOnLineBreak) throws IOException, AmbiguousTokenDefinitionsException;

    /**
     * Separates the samples by the given sampleSeparator, tokenizes the samples with the tokenDefinitions
     * and runs the whole grammar inference algorithm
     *
     * @param tokenDefinitions
     * @param sampleSeparator
     * @param inputString
     * @param sampleStopOnLineBreak
     * @return the rootSymbol {@link NonTerminal} of the inferred grammar
     */
    NonTerminal inferGrammarFromString(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, String inputString, boolean sampleStopOnLineBreak) throws IOException, AmbiguousTokenDefinitionsException;


    /**
     * --------------------- Specialized Methods -----------------------------------
     */

    /**
     * Infers a grammar from the given samples using the given tokenDefinitions
     *  @param tokenDefinitions
     * @param samples
     */
    NonTerminal inferGrammar(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, List<String> samples, int rootSampleNr) throws AmbiguousTokenDefinitionsException;

    /**
     * Infers a grammar from the given samples, using the given rootSymbol as initial grammar for the mutation algorithm
     * the alreadyAcceptedSamples are option, but will highly increase the accuracy and performance of the algorithm
     * @param tokenDefinitions
     * @param sampleSeparator
     * @param samples
     * @param alreadyAcceptedSamples
     * @param rootSymbol
     * @return
     */
    NonTerminal inferGrammarFromString(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, List<String> samples, Set<String> alreadyAcceptedSamples, NonTerminal rootSymbol) throws AmbiguousTokenDefinitionsException;

}
