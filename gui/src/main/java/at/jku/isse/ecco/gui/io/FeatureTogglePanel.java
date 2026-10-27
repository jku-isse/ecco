package at.jku.isse.ecco.gui.io;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.FeatureSelectionPropagator;
import at.jku.isse.ecco.service.EccoService;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pane listing every feature (on and off) as a checkbox, docked in the Artifacts tab (it was an
 * always-on-top window of its own): toggling a checkbox fires {@link ConfigurationChangeListener}
 * immediately, letting a caller keep the artifact tree and code viewer live-in-sync. Deliberately simpler than
 * {@link ConfigurationPickerDialog} (no per-feature revision picker, no configuration string
 * field) - this is for quickly toggling features on/off, not composing an exact configuration
 * string; toggling a feature on always uses its latest revision, matching
 * ConfigurationPickerDialog's own default-to-latest behavior when a feature is checked without
 * picking a specific revision.
 *
 * <p>Keeps the selection consistent with accepted REQUIRES/EXCLUDES/MANDATORY feature-model
 * constraints as the user toggles checkboxes -- see {@link FeatureSelectionPropagator} (the actual
 * fixpoint algorithm, kept free of any JavaFX dependency so it's directly unit-testable) and
 * {@link #applyMandatoryLocks} here. This only affects which features end up selected here; it does
 * not change how an already-built {@link Configuration} gets composed (that's a separate, already
 * existing mechanism -- surplus-module suppression in {@code EccoService#compose}).
 *
 * <p>The features and the accepted constraints are read in the background, holding the service's
 * lock ({@link RepositoryAccess}): read on the FX thread, the window froze while a commit ran, and a
 * commit adding a feature meanwhile failed the copy of the features. So is each ticked feature's
 * latest revision, at every toggle: taken from the last read of the features, a revision committed
 * since was not seen until the pane was docked again, and a ticked feature stood for the one before.
 */
public class FeatureTogglePanel extends VBox {

	public interface ConfigurationChangeListener {
		void configurationChanged(Configuration configuration);
	}

	/**
	 * Accepted hard REQUIRES/EXCLUDES/MANDATORY suggestions, fed to {@link FeatureSelectionPropagator}
	 * and used by {@link #applyMandatoryLocks}. Read at construction and again every time the
	 * panel is shown again ({@link #reloadConstraints()}) rather than re-mined on every checkbox
	 * click, so toggling stays instant, while still picking up constraint changes made elsewhere
	 * (e.g. the Feature Model tab) without needing to recreate this panel.
	 */
	private List<ConstraintMiner.Suggestion> acceptedSuggestions = List.of();

	/** Feature names locked selected+disabled by {@link #applyMandatoryLocks}; kept in sync with it. */
	private Set<String> mandatoryFeatureNames = Set.of();

	private final EccoService service;
	private final ConfigurationChangeListener listener;
	/** By feature name, sorted. */
	private final Map<String, CheckBox> checkBoxesByFeature = new LinkedHashMap<>();
	private final VBox rowsContainer = new VBox(4);
	private final TextField filterField = new TextField();
	/** The features to tick once they are read - {@link #showConfiguration} before that. */
	private Set<String> pendingConfiguration;
	/** The reads still running. */
	private int reading;
	/** Counts the reads: one overtaken by a later one shows nothing. */
	private int requested;
	/** Counts the toggles: the configuration of one overtaken by a later toggle is not reported. */
	private int toggles;

	public FeatureTogglePanel(EccoService service, ConfigurationChangeListener listener) {
		super(8);
		this.service = service;
		this.listener = listener;

		this.filterField.setPromptText("Filter features...");
		this.filterField.textProperty().addListener((observable, oldValue, newValue) -> this.filter());

		ScrollPane scrollPane = new ScrollPane(this.rowsContainer);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportWidth(240);
		scrollPane.setPrefViewportHeight(400);

		VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
		getChildren().addAll(
				new Label("Features of the configuration:"),
				this.filterField,
				new Separator(),
				scrollPane);
		setPadding(new Insets(10));

		this.read();
	}

	/** What the panel shows of the repository: its features, and the accepted constraints. */
	private record Snapshot(List<String> features, List<ConstraintMiner.Suggestion> acceptedSuggestions) {

		/** Holding the service's lock. */
		static Snapshot of(EccoService service) {
			List<Feature> features = new ArrayList<>(service.getRepository().getFeatures());
			features.sort(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER));
			List<String> names = new ArrayList<>();
			for (Feature feature : features)
				names.add(feature.getName());
			return new Snapshot(names, service.acceptedSuggestions(service.getRepository()));
		}
	}

	/** Reads the features and constraints in the background and shows them. */
	private void read() {
		this.reading++;
		int read = ++this.requested;
		RepositoryAccess.read(this.service, "Features", Snapshot::of, snapshot -> {
			this.reading--;
			if (read == this.requested) // else overtaken by a newer read
				this.show(snapshot);
		}, failure -> {
			this.reading--;
			new ExceptionAlert(failure).show();
		}, () -> {
			this.reading--; // the repository is not open: nothing to show, nor to tick
			if (!this.reading())
				this.pendingConfiguration = null;
		});
	}

	/** Whether a read of the features or constraints is still to be shown - for tests. */
	public boolean reading() {
		return this.reading > 0;
	}

	/** Adds a checkbox for each feature not shown yet, and takes the constraints read. */
	private void show(Snapshot snapshot) {
		for (String feature : snapshot.features())
			if (!this.checkBoxesByFeature.containsKey(feature))
				this.checkBoxesByFeature.put(feature, this.checkBox(feature));
		List<CheckBox> sorted = new ArrayList<>();
		for (String feature : snapshot.features())
			sorted.add(this.checkBoxesByFeature.get(feature));
		this.rowsContainer.getChildren().setAll(sorted);
		this.acceptedSuggestions = snapshot.acceptedSuggestions();
		if (this.pendingConfiguration != null) {
			this.tick(this.pendingConfiguration);
			this.pendingConfiguration = null;
		}
		this.applyMandatoryLocks();
		this.filter();
	}

	private CheckBox checkBox(String feature) {
		CheckBox checkBox = new CheckBox(feature);
		// setOnAction (not selectedProperty()'s listener) fires only for a user-driven toggle
		// (click / spacebar), not for the propagation loop's own programmatic setSelected calls
		// below -- otherwise adjusting one checkbox to resolve a violation would recursively
		// re-trigger propagation and configurationChanged for every other checkbox it touches.
		checkBox.setOnAction(event -> {
			Set<String> selected = new HashSet<>();
			for (Map.Entry<String, CheckBox> entry : this.checkBoxesByFeature.entrySet()) {
				if (entry.getValue().isSelected()) {
					selected.add(entry.getKey());
				}
			}
			FeatureSelectionPropagator.propagate(
					selected, feature, this.acceptedSuggestions, this.mandatoryFeatureNames);
			for (Map.Entry<String, CheckBox> entry : this.checkBoxesByFeature.entrySet()) {
				entry.getValue().setSelected(selected.contains(entry.getKey()));
			}
			this.report(selected);
		});
		return checkBox;
	}

	/** Shows only the checkboxes whose feature name contains the filter's text. */
	private void filter() {
		String filter = this.filterField.getText() == null ? "" : this.filterField.getText().trim().toLowerCase();
		for (Map.Entry<String, CheckBox> entry : this.checkBoxesByFeature.entrySet()) {
			boolean matches = filter.isEmpty() || entry.getKey().toLowerCase().contains(filter);
			entry.getValue().setVisible(matches);
			entry.getValue().setManaged(matches);
		}
	}

	/** Ticks exactly the features of [configuration] - one picked with its revisions in the configuration dialog. */
	public void showConfiguration(Configuration configuration) {
		Set<String> names = new HashSet<>();
		for (FeatureRevision revision : configuration.getFeatureRevisions())
			names.add(revision.getFeature().getName());
		if (this.checkBoxesByFeature.isEmpty() && this.reading())
			this.pendingConfiguration = names; // ticked once the features are read
		else
			this.tick(names);
	}

	private void tick(Set<String> names) {
		for (Map.Entry<String, CheckBox> entry : this.checkBoxesByFeature.entrySet())
			if (!entry.getValue().isDisable()) // a mandatory feature stays ticked
				entry.getValue().setSelected(names.contains(entry.getKey()));
	}

	/**
	 * Picks up constraint changes made elsewhere (e.g. accepted in the Feature Model tab), and
	 * features committed since - called when the panel is shown again.
	 */
	public void reloadConstraints() {
		this.read();
	}

	/**
	 * Pre-checks and disables every checkbox for a feature that's the subject of an accepted
	 * MANDATORY suggestion -- sidesteps having to fight a user's own click to uncheck the one thing
	 * that has no other feature to blame for the violation. A feature that stops being mandatory
	 * (constraint un-accepted) is simply re-enabled, left checked, rather than surprising the user by
	 * unchecking it out from under them.
	 */
	private void applyMandatoryLocks() {
		Set<String> mandatoryFeatureNames = new HashSet<>();
		for (ConstraintMiner.Suggestion suggestion : this.acceptedSuggestions) {
			if (suggestion.isHard() && suggestion.kind == Constraint.Kind.MANDATORY) {
				mandatoryFeatureNames.add(suggestion.a);
			}
		}
		this.mandatoryFeatureNames = mandatoryFeatureNames;
		for (Map.Entry<String, CheckBox> entry : this.checkBoxesByFeature.entrySet()) {
			boolean mandatory = mandatoryFeatureNames.contains(entry.getKey());
			entry.getValue().setDisable(mandatory);
			if (mandatory) {
				entry.getValue().setSelected(true);
			}
		}
	}

	/** Reports the [ticked] features, each at its latest revision now - read in the background. */
	private void report(Set<String> ticked) {
		int toggle = ++this.toggles;
		RepositoryAccess.read(this.service, "Configuration", service -> configuration(service, ticked), configuration -> {
			if (toggle == this.toggles) // else overtaken by a later toggle
				this.listener.configurationChanged(configuration);
		}, failure -> new ExceptionAlert(failure).show());
	}

	/** The [ticked] features, each at its latest revision - holding the service's lock. */
	private static Configuration configuration(EccoService service, Set<String> ticked) {
		List<FeatureRevision> selectedRevisions = new ArrayList<>();
		for (Feature feature : service.getRepository().getFeatures())
			if (ticked.contains(feature.getName()) && feature.getLatestRevision() != null)
				selectedRevisions.add(feature.getLatestRevision());
		return service.getEntityFactory().createConfiguration(selectedRevisions.toArray(new FeatureRevision[0]));
	}
}
