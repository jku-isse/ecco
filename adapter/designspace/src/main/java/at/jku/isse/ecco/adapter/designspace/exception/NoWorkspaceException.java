package at.jku.isse.ecco.adapter.designspace.exception;

public class NoWorkspaceException extends IllegalArgumentException{

    private static final String EXCEPTION_MESSAGE = "no workspace was passed";

    public NoWorkspaceException() {
        super(EXCEPTION_MESSAGE);
    }
}
