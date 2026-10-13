package at.jku.isse.ecco.cli.command;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public interface Command {

    void run(Namespace namespace);

    /**
     * Runs [action] with the repository open, then closes it. Closing after a failure can fail too -
     * a commit that ran out of memory left its transaction open, and close() then reported only "Not
     * all transactions have been ended", in place of what went wrong. Such a failure to close is
     * attached to the action's own.
     */
    static void withOpenRepository(EccoService eccoService, Runnable action) {
        eccoService.open();
        Throwable failure = null;
        try {
            action.run();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            try {
                eccoService.close();
            } catch (RuntimeException e) {
                if (failure == null)
                    throw e;
                failure.addSuppressed(e);
            }
        }
    }
}
