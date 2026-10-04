package at.jku.isse.ecco.adapter.lilypond.test.thesis;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class JavaFxLauncher extends Application {
    private final static Object launchBarrier = new Object();
    public static Stage stage;
    public static boolean initialized;

    public static void setScene(Scene scene) throws InterruptedException {
        Platform.runLater(() -> {
            stage.setScene(scene);
            stage.show();
        });
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;

        stage.setOnCloseRequest(Event::consume);

        synchronized (launchBarrier) {
            launchBarrier.notify();
        }
    }

    public static void initialize() throws InterruptedException {
        initialized = true;
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
