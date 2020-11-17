package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.Collection;

public class PluginsView extends TableView<PluginsView.PluginInfo> implements EccoListener {

	private EccoService service;

	private ObservableList<PluginInfo> pluginsData = FXCollections.observableArrayList();

	private boolean initialized = false;


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


		this.initialized = false;

		service.addListener(this);

		this.statusChangedEvent(service);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
				this.setDisable(false);
			});
			if (!this.initialized) {
				Collection<ArtifactPlugin> plugins = service.getArtifactPlugins();
				Platform.runLater(() -> {
					for (ArtifactPlugin ap : plugins) {
						this.pluginsData.add(new PluginInfo(ap.getPluginId(), ap.getName(), ap.getDescription()));
						this.initialized = true;
					}
				});
			}
		} else {
			Platform.runLater(() -> {
				this.setDisable(true);
			});
		}
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
