package at.jku.isse.ecco.exceptions;

public class PreprocessorSyntaxErrorException extends Exception {

	private static final long serialVersionUID = 1L;

	public PreprocessorSyntaxErrorException() {
		super();
	}
	
	public PreprocessorSyntaxErrorException(String message) {
		super(message);
	}
	
	public PreprocessorSyntaxErrorException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public PreprocessorSyntaxErrorException(Throwable cause) {
		super(cause);
	}

}
