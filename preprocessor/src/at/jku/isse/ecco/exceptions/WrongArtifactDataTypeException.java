package at.jku.isse.ecco.exceptions;

public class WrongArtifactDataTypeException extends Exception {

	private static final long serialVersionUID = 1L;

	public WrongArtifactDataTypeException() {
		super();
	}
	
	public WrongArtifactDataTypeException(String message) {
		super(message);
	}
	
	public WrongArtifactDataTypeException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public WrongArtifactDataTypeException(Throwable cause) {
		super(cause);
	}

}
