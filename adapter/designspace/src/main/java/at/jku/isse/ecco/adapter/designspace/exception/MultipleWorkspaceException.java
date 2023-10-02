package at.jku.isse.ecco.adapter.designspace.exception;

public class MultipleWorkspaceException extends UnsupportedOperationException {

    private static final String EXCEPTION_MESSAGE = "creating an ecco tree with multiple workspaces is not supported";

    public MultipleWorkspaceException() {
        super(EXCEPTION_MESSAGE);
    }
}
