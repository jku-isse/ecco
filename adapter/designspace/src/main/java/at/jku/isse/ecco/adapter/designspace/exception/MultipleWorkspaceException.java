package at.jku.isse.ecco.adapter.designspace.exception;

public class MultipleWorkspaceException extends UnsupportedOperationException {
    public MultipleWorkspaceException() {
        super("creating an ecco tree with multiple workspaces is not supported");
    }
}
