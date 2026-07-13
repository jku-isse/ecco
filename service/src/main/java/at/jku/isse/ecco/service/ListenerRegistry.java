package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.service.listener.ServerListener;
import at.jku.isse.ecco.service.listener.WriteListener;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the {@link EccoListener} registry and the write-in-progress flag for one {@link EccoService}
 * instance, and broadcasts service events to registered listeners.
 */
public class ListenerRegistry {

    private static final Logger LOGGER = Logger.getLogger(ListenerRegistry.class.getName());

    private final EccoService owner;

    // CopyOnWriteArrayList: fire*Event methods can now run on background threads (e.g.
    // addVariant()/safeTransaction() firing after a GUI action's own background Thread/Task), while
    // GUI dialogs add/removeListener from the FX thread -- a plain ArrayList would risk
    // ConcurrentModificationException across those threads.
    private final Collection<EccoListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Advisory-only signal for GUI code: true while a write operation that mutates the live, shared
     * {@code Association}/{@code AssociationCounter} object graph (or persists other repository
     * state) is in flight. Does NOT provide mutual exclusion by itself -- {@code commit()} etc. are
     * already {@code synchronized} on the owning {@code EccoService} instance, which serializes THEM
     * against each other, but NOT against GUI code that holds a direct reference to an
     * {@code Association} object and calls methods on it later, entirely outside any
     * {@code EccoService} synchronization (e.g. {@code ArtifactsView} rendering
     * {@code association.computeCondition()} on the FX thread). That is a real, pre-existing
     * unsynchronized-access gap in the core object model, not fixed here -- this flag only lets
     * speculative GUI background reads (constraint-violation checks, etc.) opt to skip themselves
     * while a write is in progress, reducing how often background read traffic overlaps that
     * unprotected window, without pretending to close it.
     */
    private volatile boolean writeInProgress = false;

    public ListenerRegistry(EccoService owner) {
        this.owner = owner;
    }

    public boolean isWriteInProgress() {
        return this.writeInProgress;
    }

    public void setWriteInProgress(boolean writeInProgress) {
        this.writeInProgress = writeInProgress;
    }

    public void addListener(EccoListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(EccoListener listener) {
        this.listeners.remove(listener);
    }


    // service events

    // A listener throwing must never abort the broadcast to the remaining listeners -- and, since
    // fire*Event methods are called from inside write-transaction try blocks (e.g. addVariant(),
    // safeTransaction()), an uncaught exception here would be caught by that method's own
    // catch-and-rollback, incorrectly rolling back a transaction that already committed
    // (repositoryDao.store()/transactionStrategy.end() already ran). Isolating each listener call
    // makes that impossible.
    public void fireStatusChangedEvent() {
        for (EccoListener listener : this.listeners) {
            try {
                listener.statusChangedEvent(this.owner);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Listener threw during statusChangedEvent; notifying remaining listeners.", e);
            }
        }
    }

    public void fireOperationProgressEvent(String operationString, double progress) {
        for (EccoListener listener : this.listeners) {
            try {
                listener.operationProgressEvent(this.owner, operationString, progress);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Listener threw during operationProgressEvent; notifying remaining listeners.", e);
            }
        }
    }

    public void fireReadEvent(Path path, ArtifactReader reader) {
        for (ReadListener listener : this.listeners) {
            try {
                listener.fileReadEvent(path, reader);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Listener threw during fileReadEvent; notifying remaining listeners.", e);
            }
        }
    }

    public void fireWriteEvent(Path path, ArtifactWriter writer) {
        for (WriteListener listener : this.listeners) {
            try {
                listener.fileWriteEvent(path, writer);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Listener threw during fileWriteEvent; notifying remaining listeners.", e);
            }
        }
    }


    // repository events

    public void fireAssociationSelectedEvent(Association association) {
        for (EccoListener listener : this.listeners) {
            try {
                listener.associationSelectedEvent(this.owner, association);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Listener threw during associationSelectedEvent; notifying remaining listeners.", e);
            }
        }
    }


    // server events

    public void fireServerEvent(String message) {
        for (ServerListener listener : this.listeners) {
            listener.serverEvent(this.owner, message);
        }
    }

    public void fireServerStartedEvent(int port) {
        for (ServerListener listener : this.listeners) {
            listener.serverStartEvent(this.owner, port);
        }
    }

    public void fireServerStoppedEvent() {
        for (ServerListener listener : this.listeners) {
            listener.serverStopEvent(this.owner);
        }
    }

}
