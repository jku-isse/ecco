package at.jku.isse.ecco.gui.io;

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
import java.util.function.Consumer;

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
	/** The string as last parsed, if it is a configuration - null while it is parsed or is none. */
	private Configuration parsed;
	/** Counts the parses started: only the latest one's result is shown. */
	private int parses;
	private boolean parsing;
	/** Whether the features are still being read. */
	private boolean reading;

	public ConfigurationPickerDialog(EccoService service) {
		this(service, "");
	}

	/** Starting from [initial], the text of the field it was opened for. */
	public ConfigurationPickerDialog(EccoService service, String initial) {
		at.jku.isse.ecco.gui.Dialogs.ownByFocusedWindow(this);
		setTitle("Exact Configuration"); // named as the button that opens it
		setHeaderText("Check a feature to include it below, or type/edit the configuration string directly - they stay in sync.");
		setResizable(true);

		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Map<String, ComboBox<FeatureRevision>> comboBoxesByFeature = new LinkedHashMap<>();
		Map<String, CheckBox> checkBoxesByFeature = new LinkedHashMap<>();
		Map<HBox, String> rowSearchNames = new LinkedHashMap<>();
		VBox rowsContainer = new VBox(6);

		TextField configStringField = this.configStringField;
		// guards against the field <-> checkbox <-> combo box listeners re-triggering each other
		boolean[] updating = {false};

		// ticks the features the string names, with the named revisions - as last parsed; while the
		// typed string is incomplete or invalid the checkboxes/combo boxes stay as they are
		Runnable showString = () -> {
			Configuration parsed = this.parsed;
			if (updating[0] || parsed == null) return;
			updating[0] = true;
			Set<FeatureRevision> selectedRevisions = new HashSet<>(Arrays.asList(parsed.getFeatureRevisions()));
			for (String feature : comboBoxesByFeature.keySet()) {
				ComboBox<FeatureRevision> revisionComboBox = comboBoxesByFeature.get(feature);
				FeatureRevision match = revisionComboBox.getItems().stream()
						.filter(selectedRevisions::contains)
						.findFirst().orElse(null);
				revisionComboBox.setValue(match);
				checkBoxesByFeature.get(feature).setSelected(match != null);
			}
			updating[0] = false;
		};

		// a row per feature, once the features are read
		Consumer<List<FeatureRow>> showFeatures = features -> {
			for (FeatureRow feature : features) {
				CheckBox includeCheckBox = new CheckBox();

				Label nameLabel = new Label(feature.name());
				nameLabel.setMinWidth(150);
				nameLabel.setPrefWidth(150);

				ComboBox<FeatureRevision> revisionComboBox = new ComboBox<>();
				revisionComboBox.getItems().addAll(feature.revisions());
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
							FeatureRevision latest = feature.latest();
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
				comboBoxesByFeature.put(feature.name(), revisionComboBox);
				checkBoxesByFeature.put(feature.name(), includeCheckBox);
				rowSearchNames.put(row, feature.name().toLowerCase());
				rowsContainer.getChildren().add(row);
			}
		};

		configStringField.setPromptText("e.g. FeatureA.revisionId, FeatureB");
		configStringField.textProperty().addListener((observable, oldValue, newValue) -> this.parse(service, showString));
		// OK only for a configuration that parses - OK with an invalid one failed in the result converter

		TextField filterField = new TextField();
		filterField.setPromptText("Filter features...");
		Runnable filterRows = () -> {
			String filter = filterField.getText().trim().toLowerCase();
			for (HBox row : rowSearchNames.keySet()) {
				boolean matches = filter.isEmpty() || rowSearchNames.get(row).contains(filter);
				row.setVisible(matches);
				row.setManaged(matches);
			}
		};
		filterField.textProperty().addListener((observable, oldValue, newValue) -> filterRows.run());

		// the features read in the background, holding the service's lock: read on the FX thread, the
		// window froze while a commit ran, and a commit adding a feature failed the copy of them
		this.reading = true;
		at.jku.isse.ecco.gui.RepositoryAccess.read(service, "Configuration features", FeatureRow::read, features -> {
			this.reading = false;
			showFeatures.accept(features);
			showString.run(); // the string given or typed meanwhile
			filterRows.run();
		}, failure -> {
			this.reading = false;
			new at.jku.isse.ecco.gui.ExceptionAlert(failure).show();
		}, () -> this.reading = false); // the repository closed: nothing to show

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
			return this.parsed; // OK is enabled only once the string as it is now was parsed
		});
		configStringField.setText(initial == null ? "" : initial);
		this.parse(service, showString);
	}

	/**
	 * Parses the string in the background and then, unless it was changed meanwhile, shows it (or
	 * why it is no configuration) and enables OK. parseConfigurationString waits for the service's
	 * lock: on the FX thread, at every keystroke, typing froze behind a running commit (NFR-C3).
	 */
	private void parse(EccoService service, Runnable showParsed) {
		String text = this.configStringField.getText();
		int parse = ++this.parses;
		this.parsed = null;
		this.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
		if (text == null || text.isBlank()) {
			this.parsing = false;
			this.showProblem("");
			return;
		}
		this.parsing = true;
		at.jku.isse.ecco.gui.RepositoryAccess.read(service, "Configuration", s -> {
			try {
				return new Parsed(s.parseConfigurationString(text), null);
			} catch (RuntimeException e) {
				return new Parsed(null, e.getMessage() == null ? "Not a configuration." : e.getMessage());
			}
		}, result -> {
			if (parse != this.parses)
				return; // typed on meanwhile
			this.parsing = false;
			this.parsed = result.configuration();
			this.showProblem(result.problem());
			showParsed.run();
		}, failure -> {
			if (parse != this.parses)
				return;
			this.parsing = false;
			this.showProblem("Could not be read: " + failure.getMessage());
		}, () -> {
			if (parse != this.parses)
				return;
			this.parsing = false;
			this.showProblem("Could not be read: the repository is not open.");
		});
	}

	private record Parsed(Configuration configuration, String problem) {
	}

	/** Says why the string is no configuration ("": nothing to say), and enables OK if it is one. */
	private void showProblem(String problem) {
		this.invalidLabel.setText(problem == null ? "" : problem);
		this.invalidLabel.setVisible(problem != null && !problem.isEmpty());
		this.invalidLabel.setManaged(this.invalidLabel.isVisible());
		this.getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
	}

	/** Whether the string is still being parsed - for tests. */
	public boolean parsing() {
		return this.parsing;
	}

	/** Whether the features are still being read - for tests. */
	public boolean reading() {
		return this.reading;
	}

	/** A feature as listed: its name, revisions and latest revision - read holding the service's lock. */
	private record FeatureRow(String name, List<FeatureRevision> revisions, FeatureRevision latest) {

		static List<FeatureRow> read(EccoService service) {
			List<Feature> features = new ArrayList<>(service.getRepository().getFeatures());
			features.sort(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER));
			List<FeatureRow> rows = new ArrayList<>();
			for (Feature feature : features)
				rows.add(new FeatureRow(feature.getName(), List.copyOf(feature.getRevisions()), feature.getLatestRevision()));
			return rows;
		}
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
	private static String buildConfigurationString(Map<String, ComboBox<FeatureRevision>> comboBoxesByFeature) {
		return at.jku.isse.ecco.feature.ConfigurationText.print(comboBoxesByFeature.values().stream()
				.map(ComboBox::getValue)
				.filter(Objects::nonNull)
				.toList(), true);
	}
}
