package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;

import java.io.IOException;

/**
 * Component that builds a ecco model from a grammar and list of parsed tokenized samples
 *
 * @author Michael Jahn
 */
public interface EccoModelBuilder {

    /**
     * Generates a ecco model using the given {@link EccoModelBuilderStrategy} to generate the nodes and artifacts from the
     * given grammar({@link NonTerminal}) and parses the given file {@link String}
     *
     * @param strategy
     * @param rootSymbol
     * @param filePath
     * @param tryWrittenParser
     * @return
     * @throws IOException
     */
    Node buildEccoModel(EccoModelBuilderStrategy strategy, NonTerminal rootSymbol, String filePath, boolean tryWrittenParser) throws IOException, AmbiguousTokenDefinitionsException;

}
