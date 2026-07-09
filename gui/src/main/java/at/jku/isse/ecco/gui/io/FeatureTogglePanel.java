package at.jku.isse.ecco.gui.io;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A non-modal, always-on-top window listing every feature (on and off) as a checkbox, for a
 * plug-and-play alternative to the Artifacts tab's Select by Configuration -> Compose Selected ->
 * click a node workflow: toggling a checkbox here fires {@link ConfigurationChangeListener}
 * immediately, letting a caller keep the artifact tree and code viewer live-in-sync instead of
 * requiring a separate compose step per change. Deliberately simpler than
 * {@link ConfigurationPickerDialog} (no per-feature revision picker, no configuration string
 * field) - this is for quickly toggling features on/off, not composing an exact configuration
 * string; toggling a feature on always uses its latest revision, matching
 * ConfigurationPickerDialog's own default-to-latest behavior when a feature is checked without
 * picking a specific revision.
 */
public class FeatureTogglePanel extends Stage {

	public interface ConfigurationChangeListener {
		void configurationChanged(Configuration configuration);
	}

	public FeatureTogglePanel(EccoService service, ConfigurationChangeListener listener) {
		initModality(Modality.NONE);
		initStyle(StageStyle.UTILITY);
		setTitle("Features");
		setAlwaysOnTop(true);

		List<? extends Feature> features = new ArrayList<>(service.getRepository().getFeatures());
		features.sort(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER));

		Map<Feature, CheckBox> checkBoxesByFeature = new LinkedHashMap<>();
		Map<CheckBox, String> searchNames = new LinkedHashMap<>();
		VBox rowsContainer = new VBox(4);

		for (Feature feature : features) {
			CheckBox checkBox = new CheckBox(feature.getName());
			checkBox.selectedProperty().addListener((observable, wasSelected, isSelected) ->
					listener.configurationChanged(buildConfiguration(service, checkBoxesByFeature)));
			checkBoxesByFeature.put(feature, checkBox);
			searchNames.put(checkBox, feature.getName().toLowerCase());
			rowsContainer.getChildren().add(checkBox);
		}

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

		VBox content = new VBox(8,
				new Label("Toggle features:"),
				filterField,
				new Separator(),
				scrollPane);
		content.setPadding(new Insets(10));

		setScene(new Scene(content));
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
