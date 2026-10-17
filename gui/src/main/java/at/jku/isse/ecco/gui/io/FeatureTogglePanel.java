package at.jku.isse.ecco.gui.io;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
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
 */
public class FeatureTogglePanel extends VBox {

	public interface ConfigurationChangeListener {
		void configurationChanged(Configuration configuration);
	}

	/**
	 * Accepted hard REQUIRES/EXCLUDES/MANDATORY suggestions, fed to {@link FeatureSelectionPropagator}
	 * and used by {@link #applyMandatoryLocks}. Cached at construction and refreshed every time the
	 * panel is shown again ({@link #reloadConstraints()}) rather than re-mined on every checkbox
	 * click, so toggling stays instant, while still picking up constraint changes made elsewhere
	 * (e.g. the Feature Model tab) without needing to recreate this panel.
	 */
	private List<ConstraintMiner.Suggestion> acceptedSuggestions;

	/** Feature names locked selected+disabled by {@link #applyMandatoryLocks}; kept in sync with it. */
	private Set<String> mandatoryFeatureNames = Set.of();

	private final EccoService service;
	private final Map<Feature, CheckBox> checkBoxesByFeature = new LinkedHashMap<>();

	public FeatureTogglePanel(EccoService service, ConfigurationChangeListener listener) {
		super(8);
		this.service = service;
		this.acceptedSuggestions = service.acceptedSuggestions(service.getRepository());

		List<? extends Feature> features = new ArrayList<>(service.getRepository().getFeatures());
		features.sort(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER));

		Map<Feature, CheckBox> checkBoxesByFeature = this.checkBoxesByFeature;
		Map<CheckBox, String> searchNames = new LinkedHashMap<>();
		VBox rowsContainer = new VBox(4);

		for (Feature feature : features) {
			CheckBox checkBox = new CheckBox(feature.getName());
			// setOnAction (not selectedProperty()'s listener) fires only for a user-driven toggle
			// (click / spacebar), not for the propagation loop's own programmatic setSelected calls
			// below -- otherwise adjusting one checkbox to resolve a violation would recursively
			// re-trigger propagation and configurationChanged for every other checkbox it touches.
			checkBox.setOnAction(event -> {
				Set<String> selected = new HashSet<>();
				for (Map.Entry<Feature, CheckBox> entry : checkBoxesByFeature.entrySet()) {
					if (entry.getValue().isSelected()) {
						selected.add(entry.getKey().getName());
					}
				}
				FeatureSelectionPropagator.propagate(
						selected, feature.getName(), this.acceptedSuggestions, this.mandatoryFeatureNames);
				for (Map.Entry<Feature, CheckBox> entry : checkBoxesByFeature.entrySet()) {
					entry.getValue().setSelected(selected.contains(entry.getKey().getName()));
				}
				listener.configurationChanged(buildConfiguration(service, checkBoxesByFeature));
			});
			checkBoxesByFeature.put(feature, checkBox);
			searchNames.put(checkBox, feature.getName().toLowerCase());
			rowsContainer.getChildren().add(checkBox);
		}

		applyMandatoryLocks(checkBoxesByFeature);

		TextField filterField = new TextField();
		filterField.setPromptText("Filter features...");
		filterField.textProperty().addListener((observable, oldValue, newValue) -> {
			String filter = newValue.trim().toLowerCase();
			for (CheckBox checkBox : searchNames.keySet()) {
				boolean matches = filter.isEmpty() || searchNames.get(checkBox).contains(filter);
				checkBox.setVisible(matches);
				checkBox.setManaged(matches);
			}
		});

		ScrollPane scrollPane = new ScrollPane(rowsContainer);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportWidth(240);
		scrollPane.setPrefViewportHeight(400);

		VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
		getChildren().addAll(
				new Label("Features of the configuration:"),
				filterField,
				new Separator(),
				scrollPane);
		setPadding(new Insets(10));
	}

	/** Ticks exactly the features of [configuration] - one picked with its revisions in the configuration dialog. */
	public void showConfiguration(Configuration configuration) {
		Set<String> names = new HashSet<>();
		for (FeatureRevision revision : configuration.getFeatureRevisions())
			names.add(revision.getFeature().getName());
		for (Map.Entry<Feature, CheckBox> entry : this.checkBoxesByFeature.entrySet())
			if (!entry.getValue().isDisable()) // a mandatory feature stays ticked
				entry.getValue().setSelected(names.contains(entry.getKey().getName()));
	}

	/** Picks up constraint changes made elsewhere (e.g. accepted in the Feature Model tab) - called when the panel is shown again. */
	public void reloadConstraints() {
		this.acceptedSuggestions = this.service.acceptedSuggestions(this.service.getRepository());
		applyMandatoryLocks(this.checkBoxesByFeature);
	}

	/**
	 * Pre-checks and disables every checkbox for a feature that's the subject of an accepted
	 * MANDATORY suggestion -- sidesteps having to fight a user's own click to uncheck the one thing
	 * that has no other feature to blame for the violation. A feature that stops being mandatory
	 * (constraint un-accepted) is simply re-enabled, left checked, rather than surprising the user by
	 * unchecking it out from under them.
	 */
	private void applyMandatoryLocks(Map<Feature, CheckBox> checkBoxesByFeature) {
		Set<String> mandatoryFeatureNames = new HashSet<>();
		for (ConstraintMiner.Suggestion suggestion : this.acceptedSuggestions) {
			if (suggestion.isHard() && suggestion.kind == Constraint.Kind.MANDATORY) {
				mandatoryFeatureNames.add(suggestion.a);
			}
		}
		this.mandatoryFeatureNames = mandatoryFeatureNames;
		for (Map.Entry<Feature, CheckBox> entry : checkBoxesByFeature.entrySet()) {
			boolean mandatory = mandatoryFeatureNames.contains(entry.getKey().getName());
			entry.getValue().setDisable(mandatory);
			if (mandatory) {
				entry.getValue().setSelected(true);
			}
		}
	}

	private static Configuration buildConfiguration(EccoService service, Map<Feature, CheckBox> checkBoxesByFeature) {
		List<FeatureRevision> selectedRevisions = new ArrayList<>();
		for (Map.Entry<Feature, CheckBox> entry : checkBoxesByFeature.entrySet()) {
			if (entry.getValue().isSelected()) {
				FeatureRevision latest = entry.getKey().getLatestRevision();
				if (latest != null) {
					selectedRevisions.add(latest);
				}
			}
		}
		return service.getEntityFactory().createConfiguration(selectedRevisions.toArray(new FeatureRevision[0]));
	}
}
