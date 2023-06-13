package at.jku.isse.ecco.adapter.designspace.exception;

public class NoWorkspaceException extends IllegalArgumentException{

    private static final String NO_WORKSPACE_WAS_PASSED = "no workspace was passed";

    public NoWorkspaceException() {
        super(NO_WORKSPACE_WAS_PASSED);
    }
}
