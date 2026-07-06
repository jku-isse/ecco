package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.service.AdapterPreferences;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lets the user activate/deactivate the discovered {@link ArtifactPlugin}s (e.g. Lilypond, Java,
 * C, C++). Deactivated adapters are skipped by {@link at.jku.isse.ecco.service.EccoService} the
 * next time a repository is opened or initialized; it does not affect an already-open repository.
 */
public class PreferencesView extends OperationView {

	public PreferencesView() {
		super();

		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Adapter Preferences");

		Button saveButton = new Button("Save");
		this.rightButtons.getChildren().setAll(saveButton);


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

		ScrollPane scrollPane = new ScrollPane(listBox);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportHeight(250);
		this.setCenter(scrollPane);

		Label noteLabel = new Label("Deactivated adapters take effect the next time a repository is opened or initialized.");
		noteLabel.setWrapText(true);
		noteLabel.setPadding(new Insets(0, 10, 10, 10));
		this.setBottom(noteLabel);

		saveButton.setOnAction(event -> {
			Set<String> newDisabledPluginIds = new HashSet<>();
			for (Map.Entry<String, CheckBox> entry : checkBoxesByPluginId.entrySet()) {
				if (!entry.getValue().isSelected()) {
					newDisabledPluginIds.add(entry.getKey());
				}
			}
			AdapterPreferences.setDisabledPluginIds(newDisabledPluginIds);
			((Stage) this.getScene().getWindow()).close();
		});

		this.fit();
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
