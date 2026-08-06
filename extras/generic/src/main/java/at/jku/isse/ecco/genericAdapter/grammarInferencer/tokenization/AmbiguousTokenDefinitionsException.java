package at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization;

/**
 * @author Michael Jahn
 */
public class AmbiguousTokenDefinitionsException extends Exception {

    public AmbiguousTokenDefinitionsException(TokenDefinition tokenDefinition, TokenDefinition tokenDefinition1) {
        super("There are overlapping tokenDefinitions defined: " + tokenDefinition.getName() + " = "
                + tokenDefinition.getRegexString() + " and " + tokenDefinition1.getName() + " = " + tokenDefinition1.getRegexString());
    }
}
