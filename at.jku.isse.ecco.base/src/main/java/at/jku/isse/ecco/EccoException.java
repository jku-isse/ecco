package at.jku.isse.ecco;

public class EccoException extends RuntimeException {

	public EccoException(String message) {
		super(message);
	}

	public EccoException(String message, Exception cause) {
		super(message, cause);
	}

	public EccoException(Exception cause) {
		super(cause);
	}

}
