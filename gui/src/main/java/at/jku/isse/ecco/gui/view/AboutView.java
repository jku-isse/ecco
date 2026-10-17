package at.jku.isse.ecco.gui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * About ECCO: what it is, which version is running, and where the project is. macOS's native
 * About item could not be used - it crashed the JVM (see {@code MainView}).
 */
public class AboutView extends VBox {

	public static final String PROJECT_URL = "https://github.com/jku-isse/ecco";

	/** @param openProjectPage opens {@link #PROJECT_URL} in the browser */
	public AboutView(Runnable openProjectPage) {
		super(8);
		this.setPadding(new Insets(20, 28, 20, 28));
		this.setAlignment(Pos.TOP_CENTER);
		this.setPrefWidth(420);

		Label name = new Label("ECCO");
		name.setStyle("-fx-font-weight: bold; -fx-font-size: 22px;");
		Label version = new Label(version());
		Label what = new Label("A feature-oriented and distributed configuration management and version control system: "
				+ "it manages the variants of a system together with the features each one has.");
		what.setWrapText(true);
		what.setAlignment(Pos.CENTER);
		what.setStyle("-fx-text-alignment: center;");
		Hyperlink project = new Hyperlink(PROJECT_URL.replaceFirst("^https://", ""));
		project.setOnAction(event -> openProjectPage.run());

		this.getChildren().addAll(name, version, what, project);
	}

	/** "Version 0.2.0" from the jar's manifest; "Development build" when run from the classes (no manifest). Package-visible for testing. */
	static String version() {
		String version = AboutView.class.getPackage() == null ? null : AboutView.class.getPackage().getImplementationVersion();
		return version == null || version.isBlank() ? "Development build" : "Version " + version;
	}
}
