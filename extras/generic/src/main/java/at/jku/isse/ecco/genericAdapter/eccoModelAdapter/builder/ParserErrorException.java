package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder;

/**
 * @author Michael Jahn
 */
public class ParserErrorException extends Exception {

    private final String parseErrorDescription;

    public ParserErrorException(String text) {
        super();
        this.parseErrorDescription = text;
    }

    public String getParseErrorDescription() {
        return parseErrorDescription;
    }
}
