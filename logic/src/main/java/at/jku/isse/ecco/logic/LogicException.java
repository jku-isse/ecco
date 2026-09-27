package at.jku.isse.ecco.logic;

public class LogicException extends RuntimeException{

    private static final long serialVersionUID = -8810832798123051376L;
    public LogicException() {
        super();
    }

    public LogicException(String message) {
        super(message);
    }

    public LogicException(String message, Throwable cause) {
        super(message, cause);
    }
}
