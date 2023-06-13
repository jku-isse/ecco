package at.jku.isse.ecco.adapter.designspace.exception;

public class MultipleWorkspaceException extends UnsupportedOperationException {

    private static final String CREATING_AN_ECCO_TREE_WITH_MULTIPLE_WORKSPACES_IS_NOT_SUPPORTED = "creating an ecco tree with multiple workspaces is not supported";

    public MultipleWorkspaceException() {
        super(CREATING_AN_ECCO_TREE_WITH_MULTIPLE_WORKSPACES_IS_NOT_SUPPORTED);
    }
}
