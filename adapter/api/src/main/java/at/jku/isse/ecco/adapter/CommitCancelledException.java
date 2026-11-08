package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.EccoException;

/**
 * Thrown by a commit that was cancelled ({@code EccoService#cancelCommit()}) before it stored
 * anything; the repository is as it was before the commit.
 */
public class CommitCancelledException extends EccoException {

    private static final long serialVersionUID = 1L;

    public CommitCancelledException() {
        super("The commit was cancelled; nothing was committed.");
    }

}
