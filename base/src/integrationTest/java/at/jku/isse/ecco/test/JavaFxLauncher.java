package at.jku.isse.ecco.test;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class JavaFxLauncher extends Application {
    private final static Object launchBarrier = new Object();
    private final static Object sceneBarrier = new Object();
    public static Stage stage;

    public static void setScene(Scene scene) throws InterruptedException {
        Platform.runLater(() -> {
            stage.setScene(scene);
            stage.show();
        });

        synchronized (sceneBarrier) {
            sceneBarrier.wait();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;

        stage.setOnCloseRequest(event -> {
            synchronized (sceneBarrier) {
                sceneBarrier.notifyAll();
            }
        });

        synchronized (launchBarrier) {
            launchBarrier.notify();
        }
    }

    public static void initialize() throws InterruptedException {
        Thread t = new Thread("JavaFX Init Thread") {
            public void run() {
                Application.launch(JavaFxLauncher.class);
            }
        };
        t.setDaemon(true);
        t.start();
        synchronized (launchBarrier) {
            launchBarrier.wait();
        }
    }
}
