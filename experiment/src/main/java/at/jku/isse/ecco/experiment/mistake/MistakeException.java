package at.jku.isse.ecco.experiment.mistake;

public class MistakeException extends RuntimeException{
    public MistakeException() {
        super();
    }

    public MistakeException(String message) {
        super(message);
    }

    public MistakeException(String message, Throwable cause) {
        super(message, cause);
    }

    public MistakeException(Throwable cause) {
        super(cause);
    }
}
