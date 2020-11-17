package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Paths;

public class EccoGui extends Application implements EccoListener {

	public static void main(String[] args) {
		Application.launch(args);
	}


	private EccoService eccoService;

	private MainView mainView;

	private Group root;

	private Stage stage;


	@Override
	public void start(Stage primaryStage) {
		// ECCO Service
		this.eccoService = new EccoService(Paths.get("").toAbsolutePath()); // create ecco service
		eccoService.detectRepository(Paths.get("").toAbsolutePath()); // detect any existing repository


		this.stage = primaryStage;


		// INIT
		Application.setUserAgentStylesheet(STYLESHEET_MODENA);
		primaryStage.setTitle("ECCO");
		this.root = new Group();
		Scene scene = new Scene(root, 800, 600);
		scene.getStylesheets().add("ecco.css");


		// TOP LEVEL
		this.mainView = new MainView(eccoService);
		// bind to take available space
		mainView.prefHeightProperty().bind(scene.heightProperty());
		mainView.prefWidthProperty().bind(scene.widthProperty());


		this.eccoService.addListener(this);


		this.updateView();


		primaryStage.setScene(scene);
		primaryStage.setMaximized(true);
		primaryStage.show();
	}


	@Override
	public void stop() {
		try {
			this.eccoService.close();
		} catch (EccoException e) {
			e.printStackTrace();
		}
	}


	private void updateView() {
		this.root.getChildren().setAll(this.mainView);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(() -> {
			this.updateView();
			if (service.isInitialized()) {
				this.stage.setTitle("ECCO - " + this.eccoService.getRepositoryDir());
			} else {
				this.stage.setTitle("ECCO");
			}
		});
	}

}
