package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.service.AdapterPreferences;
import at.jku.isse.ecco.service.LilypondPreferences;
import at.jku.isse.ecco.service.LlmPreferences;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two independent, globally-scoped (not tied to any open repository) preference sections, saved
 * together with one "Save":
 * <ul>
 *     <li>Plugins - activate/deactivate the discovered {@link ArtifactPlugin}s (e.g. Lilypond,
 *     Java, C, C++). Deactivated adapters are skipped by
 *     {@link at.jku.isse.ecco.service.EccoService} the next time a repository is opened or
 *     initialized; it does not affect an already-open repository.</li>
 *     <li>LLM Settings - the local, OpenAI-compatible endpoint the "Import from Git" feature
 *     calls to suggest a feature configuration per imported commit.</li>
 * </ul>
 */
public class PreferencesView extends OperationView {

	public PreferencesView() {
		super();

		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Preferences");

		Button saveButton = new Button("Save");
		this.rightButtons.getChildren().setAll(saveButton);


		VBox content = new VBox(10);
		content.setPadding(new Insets(10));


		// plugins
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

		ScrollPane scrollPane = new ScrollPane(listBox);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportHeight(250);

		VBox pluginsBox = new VBox(10, scrollPane, pluginsNoteLabel);
		TitledPane pluginsPane = new TitledPane("Plugins", pluginsBox);
		pluginsPane.setAnimated(false);
		pluginsPane.setCollapsible(false);
		content.getChildren().add(pluginsPane);


		// LLM settings
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
				"Expects a local, unauthenticated OpenAI-compatible chat-completions endpoint (e.g. Ollama).");
		llmHelpLabel.setWrapText(true);
		llmGridPane.add(llmHelpLabel, 0, row, 2, 1);
		row++;

		Label endpointLabel = new Label("Endpoint URL: ");
		llmGridPane.add(endpointLabel, 0, row, 1, 1);
		TextField llmEndpointUrlField = new TextField(LlmPreferences.getEndpointUrl());
		llmGridPane.add(llmEndpointUrlField, 1, row, 1, 1);
		row++;

		Label modelLabel = new Label("Model Name: ");
		llmGridPane.add(modelLabel, 0, row, 1, 1);
		TextField llmModelNameField = new TextField(LlmPreferences.getModelName());
		llmGridPane.add(llmModelNameField, 1, row, 1, 1);

		TitledPane llmPane = new TitledPane("LLM Settings", llmGridPane);
		llmPane.setAnimated(false);
		llmPane.setCollapsible(false);
		content.getChildren().add(llmPane);


		// Lilypond settings
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
			File selected = fileChooser.showOpenDialog(this.getScene().getWindow());
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
			File selected = directoryChooser.showDialog(this.getScene().getWindow());
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

		TitledPane lilypondPane = new TitledPane("Lilypond Settings", lilypondGridPane);
		lilypondPane.setAnimated(false);
		lilypondPane.setCollapsible(false);
		content.getChildren().add(lilypondPane);


		ScrollPane outerScrollPane = new ScrollPane(content);
		outerScrollPane.setFitToWidth(true);
		this.setCenter(outerScrollPane);

		saveButton.setOnAction(event -> {
			Set<String> newDisabledPluginIds = new HashSet<>();
			for (Map.Entry<String, CheckBox> entry : checkBoxesByPluginId.entrySet()) {
				if (!entry.getValue().isSelected()) {
					newDisabledPluginIds.add(entry.getKey());
				}
			}
			AdapterPreferences.setDisabledPluginIds(newDisabledPluginIds);

			LlmPreferences.setEndpointUrl(llmEndpointUrlField.getText());
			LlmPreferences.setModelName(llmModelNameField.getText());

			LilypondPreferences.setExecutablePath(lilypondExecutableField.getText());
			String searchPathsText = lilypondSearchPathsField.getText();
			List<String> searchPaths = (searchPathsText == null || searchPathsText.isBlank())
					? List.of()
					: Arrays.stream(searchPathsText.split("\\|")).filter(p -> !p.isBlank()).map(String::trim).toList();
			LilypondPreferences.setSearchPaths(searchPaths);

			((Stage) this.getScene().getWindow()).close();
		});

		this.fit();
	}

	/** Best-effort existing parent directory of a possibly-blank/invalid path, for pre-populating a chooser dialog. */
	private static java.util.Optional<Path> preselectExistingParent(String pathText) {
		if (pathText == null || pathText.isBlank()) {
			return java.util.Optional.empty();
		}
		try {
			Path parent = Path.of(pathText).getParent();
			if (parent != null && java.nio.file.Files.isDirectory(parent)) {
				return java.util.Optional.of(parent);
			}
		} catch (Exception ignored) {
		}
		return java.util.Optional.empty();
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
