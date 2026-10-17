package at.jku.isse.ecco.gui.io;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lets the user compose a {@link Configuration} either by checking/unchecking each existing
 * feature to include or exclude it (defaulting to its latest revision once included, with an
 * earlier one pickable from the dropdown if needed), or by typing/editing the resulting
 * configuration string directly - the two stay in sync with each other.
 */
public class ConfigurationPickerDialog extends Dialog<Configuration> {

	/** What the string field holds when OK is pressed - the configuration as typed, A' and all. */
	private final TextField configStringField = new TextField();
	private final Label invalidLabel = new Label();

	public ConfigurationPickerDialog(EccoService service) {
		this(service, "");
	}

	/** Starting from [initial], the text of the field it was opened for. */
	public ConfigurationPickerDialog(EccoService service, String initial) {
		setTitle("Exact Configuration"); // named as the button that opens it
		setHeaderText("Check a feature to include it below, or type/edit the configuration string directly - they stay in sync.");
		setResizable(true);

		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		List<? extends Feature> features = new ArrayList<>(service.getRepository().getFeatures());
		features.sort(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER));

		Map<Feature, ComboBox<FeatureRevision>> comboBoxesByFeature = new LinkedHashMap<>();
		Map<Feature, CheckBox> checkBoxesByFeature = new LinkedHashMap<>();
		Map<HBox, String> rowSearchNames = new LinkedHashMap<>();
		VBox rowsContainer = new VBox(6);

		TextField configStringField = this.configStringField;
		// guards against the field <-> checkbox <-> combo box listeners re-triggering each other
		boolean[] updating = {false};

		for (Feature feature : features) {
			CheckBox includeCheckBox = new CheckBox();

			Label nameLabel = new Label(feature.getName());
			nameLabel.setMinWidth(150);
			nameLabel.setPrefWidth(150);

			ComboBox<FeatureRevision> revisionComboBox = new ComboBox<>();
			revisionComboBox.getItems().addAll(feature.getRevisions());
			revisionComboBox.setValue(null);
			revisionComboBox.setMaxWidth(Double.MAX_VALUE);
			revisionComboBox.disableProperty().bind(includeCheckBox.selectedProperty().not());
			revisionComboBox.setConverter(new StringConverter<>() {
				@Override
				public String toString(FeatureRevision featureRevision) {
					return featureRevision == null ? "" : featureRevision.getId();
				}

				@Override
				public FeatureRevision fromString(String string) {
					return null;
				}
			});
			HBox.setHgrow(revisionComboBox, Priority.ALWAYS);

			revisionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
				if (updating[0]) return;
				updating[0] = true;
				configStringField.setText(buildConfigurationString(comboBoxesByFeature));
				updating[0] = false;
			});

			includeCheckBox.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
				if (updating[0]) return;
				updating[0] = true;
				if (isSelected) {
					if (revisionComboBox.getValue() == null) {
						FeatureRevision latest = feature.getLatestRevision();
						if (latest != null) {
							revisionComboBox.setValue(latest);
						}
					}
				} else {
					revisionComboBox.setValue(null);
				}
				configStringField.setText(buildConfigurationString(comboBoxesByFeature));
				updating[0] = false;
			});

			HBox row = new HBox(10, includeCheckBox, nameLabel, revisionComboBox);
			comboBoxesByFeature.put(feature, revisionComboBox);
			checkBoxesByFeature.put(feature, includeCheckBox);
			rowSearchNames.put(row, feature.getName().toLowerCase());
			rowsContainer.getChildren().add(row);
		}

		configStringField.setPromptText("e.g. FeatureA.revisionId, FeatureB");
		configStringField.textProperty().addListener((observable, oldValue, newValue) -> {
			if (updating[0]) return;
			updating[0] = true;
			try {
				Configuration parsed = service.parseConfigurationString(newValue);
				Set<FeatureRevision> selectedRevisions = new HashSet<>(Arrays.asList(parsed.getFeatureRevisions()));
				for (Feature feature : comboBoxesByFeature.keySet()) {
					ComboBox<FeatureRevision> revisionComboBox = comboBoxesByFeature.get(feature);
					FeatureRevision match = revisionComboBox.getItems().stream()
							.filter(selectedRevisions::contains)
							.findFirst().orElse(null);
					revisionComboBox.setValue(match);
					checkBoxesByFeature.get(feature).setSelected(match != null);
				}
			} catch (EccoException ignored) {
				// leave the checkboxes/combo boxes as they are while the typed string is incomplete/invalid
			}
			updating[0] = false;
		});
		// OK only for a configuration that parses - OK with an invalid one failed in the result converter
		configStringField.textProperty().addListener((observable, oldValue, newValue) -> this.validate(service));

		TextField filterField = new TextField();
		filterField.setPromptText("Filter features...");
		filterField.textProperty().addListener((observable, oldValue, newValue) -> {
			String filter = newValue.trim().toLowerCase();
			for (HBox row : rowSearchNames.keySet()) {
				boolean matches = filter.isEmpty() || rowSearchNames.get(row).contains(filter);
				row.setVisible(matches);
				row.setManaged(matches);
			}
		});

		ScrollPane scrollPane = new ScrollPane(rowsContainer);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportWidth(500);
		scrollPane.setPrefViewportHeight(350);

		this.invalidLabel.setStyle("-fx-text-fill: firebrick;");
		this.invalidLabel.setWrapText(true);
		VBox content = new VBox(8,
				new Label("Configuration string:"), configStringField, this.invalidLabel,
				new Separator(),
				filterField, scrollPane);
		content.setPadding(new Insets(10));
		getDialogPane().setContent(content);

		setResultConverter(buttonType -> {
			if (buttonType != ButtonType.OK) {
				return null;
			}
			return service.parseConfigurationString(configStringField.getText());
		});
		configStringField.setText(initial == null ? "" : initial);
		this.validate(service);
	}

	/** Disables OK, and says why, while the string is no configuration. */
	private void validate(EccoService service) {
		String text = this.configStringField.getText();
		String problem = null;
		if (text == null || text.isBlank()) {
			problem = "";
		} else {
			try {
				service.parseConfigurationString(text);
			} catch (RuntimeException e) {
				problem = e.getMessage() == null ? "Not a configuration." : e.getMessage();
			}
		}
		this.invalidLabel.setText(problem == null ? "" : problem);
		this.invalidLabel.setVisible(problem != null && !problem.isEmpty());
		this.invalidLabel.setManaged(this.invalidLabel.isVisible());
		this.getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
	}

	/** The configuration string as it stands. */
	public String text() {
		return this.configStringField.getText();
	}

	/**
	 * [field] with a "Pick..." button beside it that opens this dialog on its text and writes the
	 * configuration picked back - next to every configuration field; the dialog was reachable only
	 * from the Artifacts tab.
	 */
	public static HBox withPicker(TextField field, EccoService service) {
		Button pick = new Button("Pick...");
		pick.setTooltip(new Tooltip("Pick features with their revisions"));
		pick.setMinWidth(Region.USE_PREF_SIZE);
		pick.setOnAction(event -> {
			ConfigurationPickerDialog dialog = new ConfigurationPickerDialog(service, field.getText());
			if (field.getScene() != null)
				dialog.initOwner(field.getScene().getWindow());
			if (dialog.showAndWait().isPresent())
				field.setText(dialog.text());
		});
		HBox box = new HBox(8, field, pick);
		HBox.setHgrow(field, Priority.ALWAYS);
		box.setMaxWidth(Double.MAX_VALUE);
		return box;
	}

	// uses each revision's full id (not FeatureRevision#toString()'s 7-character-truncated display
	// form) so the resulting string round-trips correctly through parseConfigurationString().
	private static String buildConfigurationString(Map<Feature, ComboBox<FeatureRevision>> comboBoxesByFeature) {
		return at.jku.isse.ecco.feature.ConfigurationText.print(comboBoxesByFeature.values().stream()
				.map(ComboBox::getValue)
				.filter(Objects::nonNull)
				.toList(), true);
	}
}
