package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.mining.MinimizationPreferences;
import at.jku.isse.ecco.service.AdapterPreferences;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LilypondPreferences;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A single IntelliJ-style Settings dialog: a category list on the left (one entry per
 * {@link Section}), the selected category's form on the right, and one Save/Cancel pair for the
 * whole dialog. Every section's UI is built eagerly up front (see {@link #sectionsById}) so
 * switching categories never loses an edit made in a section you're not currently looking at, and
 * Save persists every field-based section together, not just the one on screen (Server is the one
 * exception - see {@link #buildServerSection}).
 * <ul>
 *     <li>Plugins - activate/deactivate the discovered {@link ArtifactPlugin}s (e.g. Lilypond,
 *     Java, C, C++). Deactivated adapters are skipped by
 *     {@link at.jku.isse.ecco.service.EccoService} the next time a repository is opened or
 *     initialized; it does not affect an already-open repository.</li>
 *     <li>LLM Settings - the local, OpenAI-compatible endpoint the "Import from Git" feature
 *     calls to suggest a feature configuration per imported commit.</li>
 *     <li>Minimization Settings - the min-witness/confidence thresholds used to re-mine accepted
 *     constraints before {@code MinimizationResults} runs.</li>
 *     <li>Lilypond Settings - where to find the Lilypond executable, if not found automatically.</li>
 *     <li>Server - start/stop the ECCO server and watch its log; embeds {@link ServerView} as-is.</li>
 * </ul>
 */
public class PreferencesView extends OperationView {

	public enum Section {
		PLUGINS("Plugins"),
		LLM("LLM"),
		MINIMIZATION("Minimization"),
		LILYPOND("Lilypond"),
		SERVER("Server");

		private final String title;

		Section(String title) {
			this.title = title;
		}
	}

	/** One section's built UI paired with the action that persists it - see each {@code buildXyzSection} method. */
	private record SectionUi(Node content, Runnable save) {
	}

	private final Map<Section, SectionUi> sectionsById = new EnumMap<>(Section.class);

	public PreferencesView(EccoService eccoService) {
		this(eccoService, Section.PLUGINS);
	}

	/** @param initialSection the category selected when the dialog opens. */
	public PreferencesView(EccoService eccoService, Section initialSection) {
		super();

		this.headerLabel.setText("Settings");
		this.setMinWidth(700);
		this.setMinHeight(420);

		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		Button saveButton = new Button("Save");
		saveButton.setDefaultButton(true);
		this.rightButtons.getChildren().setAll(saveButton);

		this.sectionsById.put(Section.PLUGINS, buildPluginsSection());
		this.sectionsById.put(Section.LLM, buildLlmSection());
		this.sectionsById.put(Section.MINIMIZATION, buildMinimizationSection());
		this.sectionsById.put(Section.LILYPOND, buildLilypondSection());
		this.sectionsById.put(Section.SERVER, buildServerSection(eccoService));

		ScrollPane contentScrollPane = new ScrollPane();
		contentScrollPane.setFitToWidth(true);

		ListView<Section> categoryList = new ListView<>();
		categoryList.getItems().setAll(Section.values());
		categoryList.setPrefWidth(160);
		categoryList.setMaxWidth(160);
		categoryList.setCellFactory(list -> new ListCell<>() {
			@Override
			protected void updateItem(Section section, boolean empty) {
				super.updateItem(section, empty);
				this.setText(empty || section == null ? null : section.title);
			}
		});
		categoryList.getSelectionModel().selectedItemProperty().addListener((observable, oldSection, newSection) -> {
			if (newSection != null) {
				contentScrollPane.setContent(this.sectionsById.get(newSection).content());
			}
		});
		categoryList.getSelectionModel().select(initialSection);

		// BorderPane, not HBox+hgrow: BorderPane's center child is always resized to fill exactly
		// the space left of categoryList, regardless of the selected section's own preferred width -
		// an HBox only grants that width, it doesn't force the child to consume it, which left each
		// GridPane-based section sized to its own preferred (narrow) width instead of stretching.
		BorderPane body = new BorderPane();
		body.setLeft(categoryList);
		body.setCenter(contentScrollPane);
		this.setCenter(body);

		saveButton.setOnAction(event -> {
			for (SectionUi sectionUi : this.sectionsById.values()) {
				sectionUi.save().run();
			}
			((Stage) this.getScene().getWindow()).close();
		});

		this.fit();
	}

	private static SectionUi buildPluginsSection() {
		ArtifactPlugin[] plugins = ArtifactPlugin.getArtifactPlugins();
		Arrays.sort(plugins, Comparator.comparing(ArtifactPlugin::getName));

		Set<String> disabledPluginIds = AdapterPreferences.getDisabledPluginIds();

		Map<String, CheckBox> checkBoxesByPluginId = new LinkedHashMap<>();
		VBox listBox = new VBox(8);
		listBox.setPadding(new Insets(10));

		if (plugins.length == 0) {
			listBox.getChildren().add(new Label("No adapters found on the classpath."));
		}

		for (ArtifactPlugin plugin : plugins) {
			// getName()/getDescription() are not guaranteed unique (e.g. two distinct "Java"
			// adapters both call themselves "JavaArtifactPlugin"), so show the owning module too
			CheckBox checkBox = new CheckBox(plugin.getName() + " (" + moduleTag(plugin.getPluginId()) + ") – " + plugin.getDescription());
			checkBox.setSelected(!disabledPluginIds.contains(plugin.getPluginId()));
			checkBoxesByPluginId.put(plugin.getPluginId(), checkBox);
			listBox.getChildren().add(checkBox);
		}

		Label pluginsNoteLabel = new Label("Deactivated adapters take effect the next time a repository is opened or initialized.");
		pluginsNoteLabel.setWrapText(true);

		VBox pluginsBox = new VBox(10, listBox, pluginsNoteLabel);
		pluginsBox.setPadding(new Insets(10));

		Runnable save = () -> {
			Set<String> newDisabledPluginIds = new HashSet<>();
			for (Map.Entry<String, CheckBox> entry : checkBoxesByPluginId.entrySet()) {
				if (!entry.getValue().isSelected()) {
					newDisabledPluginIds.add(entry.getKey());
				}
			}
			AdapterPreferences.setDisabledPluginIds(newDisabledPluginIds);
		};

		return new SectionUi(pluginsBox, save);
	}

	private static SectionUi buildLlmSection() {
		GridPane llmGridPane = new GridPane();
		llmGridPane.setHgap(10);
		llmGridPane.setVgap(10);
		llmGridPane.setPadding(new Insets(10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		llmGridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

		int row = 0;

		Label llmHelpLabel = new Label("Used by \"Import from Git\" to suggest a feature configuration per commit. " +
				"Expects a local, unauthenticated OpenAI-compatible server (e.g. Ollama).");
		llmHelpLabel.setWrapText(true);
		llmGridPane.add(llmHelpLabel, 0, row, 2, 1);
		row++;

		Label endpointLabel = new Label("Server URL: ");
		llmGridPane.add(endpointLabel, 0, row, 1, 1);
		TextField llmEndpointUrlField = new TextField(LlmPreferences.getEndpointUrl());
		llmGridPane.add(llmEndpointUrlField, 1, row, 1, 1);
		row++;

		Label modelLabel = new Label("Model Name: ");
		llmGridPane.add(modelLabel, 0, row, 1, 1);
		ComboBox<String> llmModelNameComboBox = new ComboBox<>();
		llmModelNameComboBox.setEditable(true);
		llmModelNameComboBox.setMaxWidth(Double.MAX_VALUE);
		String savedModelName = LlmPreferences.getModelName();
		if (!savedModelName.isBlank()) {
			// so the previously-saved model still shows selected even before "Refresh" is clicked
			// (e.g. the endpoint is briefly unreachable, or the user just wants to Save unrelated fields)
			llmModelNameComboBox.getItems().add(savedModelName);
		}
		llmModelNameComboBox.setValue(savedModelName);
		HBox.setHgrow(llmModelNameComboBox, Priority.ALWAYS);

		Button refreshModelsButton = new Button("Refresh");
		Label modelStatusLabel = new Label();
		modelStatusLabel.setWrapText(true);
		refreshModelsButton.setOnAction(event -> {
			refreshModelsButton.setDisable(true);
			modelStatusLabel.setText("Loading models...");
			Task<List<String>> listModelsTask = new Task<>() {
				@Override
				protected List<String> call() throws Exception {
					return new LlmFeatureSuggestionClient(llmEndpointUrlField.getText(), "").listModels();
				}

				@Override
				protected void succeeded() {
					super.succeeded();
					refreshModelsButton.setDisable(false);
					// read via the editor, not getValue(): an editable ComboBox only commits typed
					// text to getValue()/setValue() on Enter or focus-loss, same reasoning as the
					// EditableSpinner workaround elsewhere in this dialog
					String currentText = llmModelNameComboBox.getEditor().getText();
					List<String> models = this.getValue();
					llmModelNameComboBox.getItems().setAll(models);
					llmModelNameComboBox.setValue(currentText != null && !currentText.isBlank() ? currentText : models.get(0));
					modelStatusLabel.setText(models.size() + " model(s) found.");
				}

				@Override
				protected void failed() {
					super.failed();
					refreshModelsButton.setDisable(false);
					modelStatusLabel.setText("Could not list models: " + this.getException().getMessage());
				}
			};
			new Thread(listModelsTask).start();
		});

		HBox modelBox = new HBox(6, llmModelNameComboBox, refreshModelsButton);
		llmGridPane.add(modelBox, 1, row, 1, 1);
		row++;
		llmGridPane.add(modelStatusLabel, 0, row, 2, 1);

		Runnable save = () -> {
			LlmPreferences.setEndpointUrl(llmEndpointUrlField.getText());
			String modelText = llmModelNameComboBox.getEditor().getText();
			LlmPreferences.setModelName(modelText == null ? "" : modelText.trim());
		};

		return new SectionUi(llmGridPane, save);
	}

	private static SectionUi buildMinimizationSection() {
		GridPane minimizationGridPane = new GridPane();
		minimizationGridPane.setHgap(10);
		minimizationGridPane.setVgap(10);
		minimizationGridPane.setPadding(new Insets(10));

		ColumnConstraints minimizationCol1constraint = new ColumnConstraints();
		ColumnConstraints minimizationCol2constraint = new ColumnConstraints();
		minimizationCol2constraint.setFillWidth(true);
		minimizationCol2constraint.setHgrow(Priority.ALWAYS);
		minimizationGridPane.getColumnConstraints().addAll(minimizationCol1constraint, minimizationCol2constraint);

		int minimizationRow = 0;

		Label minimizationHelpLabel = new Label("Thresholds used to re-mine accepted constraints before \"Minimize Presence " +
				"Conditions\" runs (Feature Model tab). Lower thresholds catch more constraints but risk overfitting to a " +
				"small sample of configurations.");
		minimizationHelpLabel.setWrapText(true);
		minimizationGridPane.add(minimizationHelpLabel, 0, minimizationRow, 2, 1);
		minimizationRow++;

		Label minWitnessLabel = new Label("Min Witness: ");
		minimizationGridPane.add(minWitnessLabel, 0, minimizationRow, 1, 1);
		EditableSpinner minimizationMinWitnessSpinner = new EditableSpinner(1, 1000, MinimizationPreferences.getMinWitness());
		minimizationGridPane.add(minimizationMinWitnessSpinner, 1, minimizationRow, 1, 1);
		minimizationRow++;

		Label confidenceLabel = new Label("Confidence: ");
		minimizationGridPane.add(confidenceLabel, 0, minimizationRow, 1, 1);
		Spinner<Double> minimizationConfidenceSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(
				0.0, 1.0, MinimizationPreferences.getConfidence(), 0.05));
		minimizationConfidenceSpinner.setEditable(true);
		// same workaround as EditableSpinner: a plain editable Spinner does not commit typed text to
		// valueProperty() until the field loses focus, so without this, Save would read the previous
		// value if the user clicks Save right after typing without clicking away first.
		minimizationConfidenceSpinner.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				Double.parseDouble(newValue);
				minimizationConfidenceSpinner.increment(0);
			} catch (NumberFormatException ignored) {
			}
		});
		minimizationGridPane.add(minimizationConfidenceSpinner, 1, minimizationRow, 1, 1);

		Runnable save = () -> {
			MinimizationPreferences.setMinWitness(minimizationMinWitnessSpinner.getValue());
			MinimizationPreferences.setConfidence(minimizationConfidenceSpinner.getValue());
		};

		return new SectionUi(minimizationGridPane, save);
	}

	private static SectionUi buildLilypondSection() {
		GridPane lilypondGridPane = new GridPane();
		lilypondGridPane.setHgap(10);
		lilypondGridPane.setVgap(10);
		lilypondGridPane.setPadding(new Insets(10));

		ColumnConstraints lilypondCol1constraint = new ColumnConstraints();
		ColumnConstraints lilypondCol2constraint = new ColumnConstraints();
		lilypondCol2constraint.setFillWidth(true);
		lilypondCol2constraint.setHgrow(Priority.ALWAYS);
		lilypondGridPane.getColumnConstraints().addAll(lilypondCol1constraint, lilypondCol2constraint);

		int lilypondRow = 0;

		Label lilypondHelpLabel = new Label("Only needed if Lilypond isn't found automatically. Used to render .ly artifacts " +
				"as notation images. Leave blank to use the bundled default.");
		lilypondHelpLabel.setWrapText(true);
		lilypondGridPane.add(lilypondHelpLabel, 0, lilypondRow, 2, 1);
		lilypondRow++;

		Label executableLabel = new Label("Executable Path: ");
		lilypondGridPane.add(executableLabel, 0, lilypondRow, 1, 1);
		TextField lilypondExecutableField = new TextField(LilypondPreferences.getExecutablePath());
		HBox.setHgrow(lilypondExecutableField, Priority.ALWAYS);
		Button browseExecutableButton = new Button("Browse...");
		browseExecutableButton.setOnAction(event -> {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Select Lilypond Executable");
			preselectExistingParent(lilypondExecutableField.getText())
					.ifPresent(dir -> fileChooser.setInitialDirectory(dir.toFile()));
			File selected = fileChooser.showOpenDialog(browseExecutableButton.getScene().getWindow());
			if (selected != null) {
				lilypondExecutableField.setText(selected.getAbsolutePath());
			}
		});
		HBox executableBox = new HBox(6, lilypondExecutableField, browseExecutableButton);
		lilypondGridPane.add(executableBox, 1, lilypondRow, 1, 1);
		lilypondRow++;

		Label searchPathsLabel = new Label("Search Paths: ");
		lilypondGridPane.add(searchPathsLabel, 0, lilypondRow, 1, 1);
		TextField lilypondSearchPathsField = new TextField(String.join("|", LilypondPreferences.getSearchPaths()));
		HBox.setHgrow(lilypondSearchPathsField, Priority.ALWAYS);
		Button browseSearchPathButton = new Button("Add...");
		browseSearchPathButton.setOnAction(event -> {
			DirectoryChooser directoryChooser = new DirectoryChooser();
			directoryChooser.setTitle("Add Lilypond Search Path");
			File selected = directoryChooser.showDialog(browseSearchPathButton.getScene().getWindow());
			if (selected != null) {
				String existing = lilypondSearchPathsField.getText();
				String updated = (existing == null || existing.isBlank())
						? selected.getAbsolutePath()
						: existing + "|" + selected.getAbsolutePath();
				lilypondSearchPathsField.setText(updated);
			}
		});
		HBox searchPathsBox = new HBox(6, lilypondSearchPathsField, browseSearchPathButton);
		lilypondGridPane.add(searchPathsBox, 1, lilypondRow, 1, 1);
		lilypondRow++;

		Label searchPathsNoteLabel = new Label("Multiple paths are separated by \"|\" (used for Lilypond's -I \\include search path).");
		searchPathsNoteLabel.setWrapText(true);
		lilypondGridPane.add(searchPathsNoteLabel, 0, lilypondRow, 2, 1);

		Runnable save = () -> {
			LilypondPreferences.setExecutablePath(lilypondExecutableField.getText());
			String searchPathsText = lilypondSearchPathsField.getText();
			List<String> searchPaths = (searchPathsText == null || searchPathsText.isBlank())
					? List.of()
					: Arrays.stream(searchPathsText.split("\\|")).filter(p -> !p.isBlank()).map(String::trim).toList();
			LilypondPreferences.setSearchPaths(searchPaths);
		};

		return new SectionUi(lilypondGridPane, save);
	}

	/**
	 * Unlike the other sections, {@link ServerView} is a live control (start/stop, a running log),
	 * not a set of fields to persist - reused as-is, with a no-op save, since Start/Stop already
	 * take effect immediately via its own buttons rather than waiting on this dialog's Save.
	 */
	private static SectionUi buildServerSection(EccoService eccoService) {
		return new SectionUi(new ServerView(eccoService), () -> {
		});
	}

	/** Best-effort existing parent directory of a possibly-blank/invalid path, for pre-populating a chooser dialog. */
	private static Optional<Path> preselectExistingParent(String pathText) {
		if (pathText == null || pathText.isBlank()) {
			return Optional.empty();
		}
		try {
			Path parent = Path.of(pathText).getParent();
			if (parent != null && java.nio.file.Files.isDirectory(parent)) {
				return Optional.of(parent);
			}
		} catch (Exception ignored) {
		}
		return Optional.empty();
	}

	/**
	 * Derives a short module tag from a plugin id (its fully qualified class name), e.g.
	 * "at.jku.isse.ecco.adapter.challenge.JavaPlugin" -&gt; "challenge", to disambiguate adapters
	 * whose name/description otherwise look identical.
	 */
	private static String moduleTag(String pluginId) {
		String marker = ".adapter.";
		int idx = pluginId.indexOf(marker);
		if (idx < 0) {
			return pluginId;
		}
		String afterAdapter = pluginId.substring(idx + marker.length());
		int lastDot = afterAdapter.lastIndexOf('.');
		return lastDot < 0 ? afterAdapter : afterAdapter.substring(0, lastDot);
	}

}
