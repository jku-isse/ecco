package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.mining.ParallelMinimization;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The "Minimize Presence Conditions" run, shared across every GUI view that cares about it -- one
 * trigger (in the Feature Model tab's {@link at.jku.isse.ecco.gui.view.ConstraintSuggestionsView},
 * where a human naturally is right after reviewing/accepting suggestions), observed everywhere else
 * (Associations, Artifacts) instead of each view owning its own separate button and computation.
 *
 * <p>Construct exactly one instance per {@link EccoService} (see {@code MainView}) and pass it to
 * every view that needs to trigger or display results.
 */
public class MinimizationResults implements EccoListener {

    // same defaults as SuggestConstraintsCommand/ConstraintSuggestionsView
    private static final int MIN_WITNESS = 4;
    private static final double CONFIDENCE = 0.9;

    private final EccoService service;

    /** Association id -> minimized condition text (see {@code PresenceConditionMinimizer.format}). */
    private final ObservableMap<String, String> minimizedByAssociationId = FXCollections.observableHashMap();
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final BooleanProperty running = new SimpleBooleanProperty(false);

    /**
     * The in-progress run's thread, if any, so a repository close can actually stop it (see
     * {@link #statusChangedEvent}) rather than letting it keep running in the background after its
     * results are no longer wanted.
     */
    private volatile Thread runningThread;

    public MinimizationResults(EccoService service) {
        this.service = service;
        service.addListener(this);
    }

    public ObservableMap<String, String> getMinimizedByAssociationId() {
        return minimizedByAssociationId;
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    public ReadOnlyBooleanProperty runningProperty() {
        return running;
    }

    /** No-op if a run is already in progress or the repository isn't open. */
    public void run() {
        if (!service.isInitialized() || running.get()) return;
        running.set(true);
        progress.set(0);

        Thread thread = new Thread(() -> {
            try {
                // re-mine fresh (never trust cached accept-time hardness -- a later commit may have
                // added a counterexample since), filter to accepted signatures, then let
                // ParallelMinimization compile the feature model once per worker thread and process
                // every association concurrently.
                List<Set<String>> configs = ConfigurationBridge.readConfigurations(service);
                List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(MIN_WITNESS, CONFIDENCE, null).mine(configs);
                Set<String> accepted = ConstraintSuggestionPreferences.getAccepted(service.getRepositoryDir());

                List<ConstraintMiner.Suggestion> acceptedSuggestions = new ArrayList<>();
                for (ConstraintMiner.Suggestion suggestion : mined) {
                    if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
                        acceptedSuggestions.add(suggestion);
                    }
                }

                List<Association> associations = new ArrayList<>(service.getRepository().getAssociations());
                AtomicInteger completedCount = new AtomicInteger(0);
                Map<String, String> computed = ParallelMinimization.minimizeAll(associations, acceptedSuggestions, (association, minimizedText) -> {
                    Platform.runLater(() -> minimizedByAssociationId.put(association.getId(), minimizedText));
                    double fraction = associations.isEmpty() ? 1.0 : completedCount.incrementAndGet() / (double) associations.size();
                    Platform.runLater(() -> progress.set(fraction));
                });

                Platform.runLater(() -> {
                    // drop entries for associations that no longer exist (e.g. the repository
                    // changed since the last run), so a stale minimized condition never lingers
                    // for an id that isn't actually one of this run's associations
                    minimizedByAssociationId.keySet().retainAll(computed.keySet());
                    running.set(false);
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    // cancelled via statusChangedEvent below (e.g. the repository closed mid-run) --
                    // expected, not a real error, so no alert; the map gets cleared there too.
                    Platform.runLater(() -> running.set(false));
                } else {
                    Platform.runLater(() -> {
                        running.set(false);
                        new ExceptionAlert(e).show();
                    });
                }
            } finally {
                runningThread = null;
            }
        });
        this.runningThread = thread;
        thread.start();
    }

    @Override
    public void statusChangedEvent(EccoService service) {
        if (!service.isInitialized()) {
            // stop a still-running minimization instead of letting it keep grinding away in the
            // background for results nobody wants anymore: interrupts the orchestrating thread
            // (wakes it out of ParallelMinimization's blocking wait for the next association) and,
            // via ParallelMinimization's own shutdownNow() on interruption, cancels every
            // queued-but-not-yet-started association and best-effort interrupts whatever's still
            // actively running.
            Thread thread = this.runningThread;
            if (thread != null) thread.interrupt();
            Platform.runLater(minimizedByAssociationId::clear);
        }
    }
}
