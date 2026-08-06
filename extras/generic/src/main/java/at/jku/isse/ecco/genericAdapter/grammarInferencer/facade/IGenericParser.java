package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;

import java.io.IOException;
import java.util.List;

/**
 * @author Michael Jahn
 */
public interface IGenericParser {


    /**
     * uses the given grammar, specified by a {@link NonTerminal} to parse the sample and returns the parsed rules as {@link List< Rule >}
     *
     * @param rootSymbol
     * @param sample
     * @return {@link List<Rule>} or null if an error occured, or the sample could not be parsed with the given grammar
     */
    List<Rule> parseSample(NonTerminal rootSymbol, String sample);


    List<TokenValue> getTokenValueList(NonTerminal rootSymbol, String sample);

    /**
     * uses the given grammar to parse the file
     *
     * @param rootSymbol
     * @param filePath
     * @return true, if the file could be parsed successfully, false otherwise
     */
    boolean parseFile(NonTerminal rootSymbol, String filePath) throws IOException, AmbiguousTokenDefinitionsException;

}
