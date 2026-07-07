package at.jku.isse.ecco.util.directory;

public class DirectoryException extends Exception {
    public DirectoryException() {
        super();
    }

    public DirectoryException(String message) {
        super(message);
    }

    public DirectoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public DirectoryException(Throwable cause) {
        super(cause);
    }
}
