package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Paths;

public class EccoGui extends Application implements EccoListener {

	public static void main(String[] args) {
		Application.launch(args);
	}


	private EccoService eccoService;

	private MainView mainView;

	private NotificationPanel notificationPanel;

	private StackPane root;

	private Stage stage;


	// minimum time the splash stays up, even when initialization itself is near-instant (e.g. a
	// fresh/empty repository dir) - otherwise the whole splash-to-main transition can happen inside
	// a single frame, so fast the logo never gets a chance to actually paint before it's gone.
	private static final int MIN_SPLASH_MILLIS = 1800;

	@Override
	public void start(Stage primaryStage) {
		this.stage = primaryStage;

		Stage splashStage = this.buildSplashStage();
		splashStage.show();
		splashStage.toFront();

		// EccoService construction + repository detection off the FX thread, so the splash is
		// actually visible/responsive rather than the OS just showing a blank window while this runs.
		Task<EccoService> initTask = new Task<EccoService>() {
			@Override
			public EccoService call() throws InterruptedException {
				long start = System.currentTimeMillis();
				EccoService service = new EccoService(Paths.get("").toAbsolutePath());
				service.detectRepository(Paths.get("").toAbsolutePath());
				long remaining = MIN_SPLASH_MILLIS - (System.currentTimeMillis() - start);
				if (remaining > 0) {
					Thread.sleep(remaining);
				}
				return service;
			}

			@Override
			public void succeeded() {
				super.succeeded();
				EccoGui.this.eccoService = this.getValue();
				// show (and raise) the main stage BEFORE closing the splash, so there's no gap where
				// no window is focused/frontmost and no risk of the splash ending up behind it.
				EccoGui.this.showMainStage(primaryStage);
				primaryStage.toFront();
				splashStage.close();
			}

			@Override
			public void failed() {
				super.failed();
				splashStage.close();
				new ExceptionAlert(this.getException()).showAndWait();
				Platform.exit();
			}
		};
		new Thread(initTask).start();
	}

	private static Image loadLogoImage() {
		return new Image(EccoGui.class.getResource("/ecco-logo.png").toExternalForm());
	}

	private Stage buildSplashStage() {
		ImageView logoView = new ImageView(loadLogoImage());
		logoView.setPreserveRatio(true);
		logoView.setFitWidth(360);

		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setMaxSize(24, 24);

		VBox splashRoot = new VBox(16, logoView, progressIndicator);
		splashRoot.setAlignment(Pos.CENTER);
		splashRoot.setPadding(new Insets(24));
		splashRoot.setStyle("-fx-background-color: #0b0f1a;");

		Stage splashStage = new Stage(StageStyle.UNDECORATED);
		splashStage.setAlwaysOnTop(true);
		splashStage.setResizable(false);
		splashStage.setScene(new Scene(splashRoot));
		splashStage.centerOnScreen();
		return splashStage;
	}

	private void showMainStage(Stage primaryStage) {
		// INIT
		Application.setUserAgentStylesheet(STYLESHEET_MODENA);
		primaryStage.setTitle("ECCO");
		primaryStage.getIcons().add(loadLogoImage());
		this.root = new StackPane();
		Scene scene = new Scene(root, 800, 600);
		scene.getStylesheets().add("ecco.css");


		// TOP LEVEL
		this.mainView = new MainView(eccoService);
		// bind to take available space
		mainView.prefHeightProperty().bind(scene.heightProperty());
		mainView.prefWidthProperty().bind(scene.widthProperty());

		// floating overlay for non-blocking notifications - see Notifications
		this.notificationPanel = new NotificationPanel();
		StackPane.setAlignment(notificationPanel, Pos.BOTTOM_RIGHT);
		Notifications.install(notificationPanel);


		this.eccoService.addListener(this);


		this.updateView();


		primaryStage.setScene(scene);
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
		this.root.getChildren().setAll(this.mainView, this.notificationPanel);
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
