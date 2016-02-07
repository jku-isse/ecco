package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.nio.file.Path;
import java.util.Collection;

public class PluginsView extends TableView<PluginsView.PluginInfo> implements EccoListener {

	private EccoService service;

	private ObservableList<PluginInfo> pluginsData = FXCollections.observableArrayList();


	public PluginsView(EccoService service) {
		this.service = service;


		this.setEditable(false);
		this.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<PluginInfo, String> pluginIdCol = new TableColumn<>("Id");
		TableColumn<PluginInfo, String> pluginNameCol = new TableColumn<>("Name");
		TableColumn<PluginInfo, String> pluginDescriptionCol = new TableColumn<>("Description");
		TableColumn<PluginInfo, String> pluginsCol = new TableColumn<>("Plugins");

		pluginsCol.getColumns().setAll(pluginIdCol, pluginNameCol, pluginDescriptionCol);
		this.getColumns().setAll(pluginsCol);

		pluginIdCol.setCellValueFactory((TableColumn.CellDataFeatures<PluginInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getId()));
		pluginNameCol.setCellValueFactory((TableColumn.CellDataFeatures<PluginInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getName()));
		pluginDescriptionCol.setCellValueFactory((TableColumn.CellDataFeatures<PluginInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getDescription()));

		this.setItems(this.pluginsData);


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
				this.setDisable(false);
			});
			Collection<ArtifactPlugin> plugins = service.getArtifactPlugins();
			Platform.runLater(() -> {
				for (ArtifactPlugin ap : plugins) {
					this.pluginsData.add(new PluginInfo(ap.getPluginId(), ap.getName(), ap.getDescription()));
				}
			});
		} else {
			Platform.runLater(() -> {
				this.setDisable(true);
			});
		}
	}

	@Override
	public void commitsChangedEvent(EccoService service, Commit commit) {

	}

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {

	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {

	}


	public static class PluginInfo {
		private String id, name, description;

		public PluginInfo(String id, String name, String description) {
			this.id = id;
			this.name = name;
			this.description = description;
		}

		public String getId() {
			return this.id;
		}

		public String getName() {
			return this.name;
		}

		public String getDescription() {
			return this.description;
		}
	}

}
