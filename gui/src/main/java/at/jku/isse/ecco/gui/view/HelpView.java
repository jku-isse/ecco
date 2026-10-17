package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.ribbon.RibbonAction;
import at.jku.isse.ecco.gui.ribbon.RibbonGroup;
import at.jku.isse.ecco.gui.ribbon.RibbonTabSpec;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/**
 * What the ribbon's buttons do, tab by tab - read from the ribbon itself (each button's label and
 * tooltip), so it says what the buttons say - the shortcuts, and a link to the documentation.
 * There was no help in the application.
 */
public class HelpView extends ScrollPane {

	/**
	 * @param tabs      the ribbon's tabs, in their order
	 * @param shortcuts what each shortcut does, in the order to list them ("Cmd/Ctrl+N" -> "New...")
	 * @param openDocs  opens the documentation (the README) in the browser
	 */
	public HelpView(List<RibbonTabSpec> tabs, Map<String, String> shortcuts, Runnable openDocs) {
		VBox content = new VBox(6);
		content.setPadding(new Insets(14));
		content.setPrefWidth(620);

		Label intro = new Label("ECCO keeps variants of a system together with the features each one has, learns which artifacts "
				+ "implement which features, and composes variants for feature combinations never committed as such. "
				+ "Open or create a repository, commit variants with their configurations, explore what ECCO learned, and check out new ones.");
		intro.setWrapText(true);
		content.getChildren().add(intro);

		// the dialogs name three folders - they said repository directory, base directory, directory, folder
		content.getChildren().add(heading("Folders"));
		GridPane folders = new GridPane();
		folders.setHgap(14);
		folders.setVgap(3);
		String[][] names = {
				{"Repository folder", "The folder you work in. The repository is the .ecco folder in it - you never pick .ecco itself."},
				{"Variant folder", "A folder with one variant of the system, to commit with its configuration."},
				{"Checkout folder", "Where a checkout writes a variant - best an empty folder: one that is not is emptied first, after asking."},
				{"Working folder", "The last variant or checkout folder used, shown under Status."}};
		for (int i = 0; i < names.length; i++) {
			folders.add(name(names[i][0]), 0, i);
			folders.add(description(names[i][1]), 1, i);
		}
		content.getChildren().add(folders);

		for (RibbonTabSpec tab : tabs) {
			content.getChildren().add(heading(tab.label()));
			GridPane buttons = new GridPane();
			buttons.setHgap(14);
			buttons.setVgap(3);
			int row = 0;
			for (RibbonGroup group : tab.groups())
				for (RibbonAction action : group.actions()) {
					buttons.add(name(action.label()), 0, row);
					buttons.add(description(action.tooltip() == null ? "" : action.tooltip()), 1, row++);
				}
			content.getChildren().add(buttons);
		}

		content.getChildren().add(heading("Shortcuts"));
		GridPane keys = new GridPane();
		keys.setHgap(14);
		keys.setVgap(3);
		int row = 0;
		for (Map.Entry<String, String> shortcut : shortcuts.entrySet()) {
			keys.add(name(shortcut.getKey()), 0, row);
			keys.add(description(shortcut.getValue()), 1, row++);
		}
		content.getChildren().add(keys);

		Hyperlink docs = new Hyperlink("The documentation: concepts, the command line, adapters, distributed operations");
		docs.setOnAction(event -> openDocs.run());
		VBox.setMargin(docs, new Insets(10, 0, 0, -4));
		content.getChildren().add(docs);

		this.setContent(content);
		this.setFitToWidth(true);
		this.setPrefViewportHeight(560);
	}

	/** Never cut short: "Cmd/Ctrl+1 to 7" was "Cmd/Ctrl...". */
	private static Label name(String text) {
		Label name = new Label(text);
		name.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		return name;
	}

	private static Label description(String text) {
		Label description = new Label(text);
		description.setWrapText(true);
		return description;
	}

	private static Label heading(String text) {
		Label heading = new Label(text);
		heading.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
		VBox.setMargin(heading, new Insets(10, 0, 2, 0));
		return heading;
	}
}
