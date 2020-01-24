package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.RootNode;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;

import java.util.Collection;

public class ArtifactTreeView extends BorderPane {

	private final EccoService service;

	private final ArtifactTreeTableView artifactTreeTableView;

	public ArtifactTreeView(final EccoService service) {
		this.service = service;

		final ArtifactDetailView artifactDetailView = new ArtifactDetailView(service);


		// toolbar
		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button markSelectedButton = new Button("Mark Selected");
		Button splitMarkedButton = new Button("Split Marked");
		splitMarkedButton.setDisable(true);

		toolBar.getItems().addAll(markSelectedButton, splitMarkedButton, new Separator());


		// artifact tree table view
		this.artifactTreeTableView = new ArtifactTreeTableView();

		this.artifactTreeTableView.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				artifactDetailView.showTree(newValue.getValue());
			}
		});


		// split panes
		SplitPane artifactsSplitPane = new SplitPane();
		artifactsSplitPane.setOrientation(Orientation.HORIZONTAL);
		artifactsSplitPane.getItems().addAll(this.artifactTreeTableView, artifactDetailView);


		this.setCenter(artifactsSplitPane);


		markSelectedButton.setOnAction(e -> {
			toolBar.setDisable(true);

			this.artifactTreeTableView.markSelected();

			toolBar.setDisable(false);
		});

//		splitMarkedButton.setOnAction(new EventHandler<ActionEvent>() {
//			@Override
//			public void handle(ActionEvent e) {
//				toolBar.setDisable(true);
//
//				Task extractTask = new Task<Void>() {
//					@Override
//					public Void call() throws EccoException {
//						service.split();
//
//						Platform.runLater(() -> {
//							ArtifactsView.this.refresh();
//						});
//						return null;
//					}
//
//					public void finished() {
//						toolBar.setDisable(false);
//					}
//
//					@Override
//					public void succeeded() {
//						super.succeeded();
//						this.finished();
//
//						Alert alert = new Alert(Alert.AlertType.INFORMATION);
//						alert.setTitle("Extraction Successful");
//						alert.setHeaderText("Extraction Successful");
//						alert.setContentText("Extraction Successful!");
//
//						alert.showAndWait();
//					}
//
//					@Override
//					public void cancelled() {
//						super.cancelled();
//					}
//
//					@Override
//					public void failed() {
//						super.failed();
//						this.finished();
//
//						ExceptionAlert alert = new ExceptionAlert(this.getException());
//						alert.setTitle("Extraction Error");
//						alert.setHeaderText("Extraction Error");
//
//						alert.showAndWait();
//					}
//				};
//
//				new Thread(extractTask).start();
//			}
//		});
	}

	public void setRootNode(RootNode rootNode) {
		this.artifactTreeTableView.setRootNode(rootNode);
	}

	public void setAssociationInfo(Collection<ArtifactsView.AssociationInfo> associationInfos) {
		this.artifactTreeTableView.setAssociationInfo(associationInfos);
	}

}
