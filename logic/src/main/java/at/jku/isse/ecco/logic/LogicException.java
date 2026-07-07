package at.jku.isse.ecco.logic;

public class LogicException extends RuntimeException{
    public LogicException() {
        super();
    }

    public LogicException(String message) {
        super(message);
    }
}
