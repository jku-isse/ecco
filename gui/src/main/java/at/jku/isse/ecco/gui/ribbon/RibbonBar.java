package at.jku.isse.ecco.gui.ribbon;

import atlantafx.base.controls.Tab;
import atlantafx.base.controls.TabLine;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ribbon-style replacement for a {@link javafx.scene.control.MenuBar}: a row of tabs (AtlantaFX's
 * {@link TabLine}) with a persistent "band" of buttons below it, swapped per selected tab.
 * Navigation-only - each button just runs the same {@link Runnable} a {@code MenuItem}'s
 * {@code onAction} would have called; no content view's own internal toolbar is touched by this
 * control.
 * <p>
 * {@link TabLine} itself is a bare tab strip with no content-swap-on-select behavior (unlike
 * {@link javafx.scene.control.TabPane}), so this class does that wiring by hand: every tab's band
 * is built once up front (the same eager-construction style {@code MainView} already uses for its
 * content views) and cached, then swapped into a {@link StackPane} on selection change.
 */
public class RibbonBar extends VBox {

	private final Map<RibbonAction, Button> buttonsByAction = new IdentityHashMap<>();

	public RibbonBar(List<RibbonTabSpec> tabSpecs) {
		this.getStyleClass().add("ribbon-bar");

		TabLine tabLine = new TabLine();
		tabLine.getStyleClass().add(Styles.DENSE);
		// these are navigation tabs, not documents - closing/reordering/pinning make no sense here
		tabLine.setTabClosingPolicy(Tab.ClosingPolicy.NO_TABS);

		StackPane bandArea = new StackPane();

		Map<Tab, HBox> bandsByTab = new IdentityHashMap<>();
		for (RibbonTabSpec spec : tabSpecs) {
			Tab tab = new Tab(spec.label());
			tab.setGraphic(new FontIcon(spec.icon()));
			tabLine.getTabs().add(tab);
			bandsByTab.put(tab, buildBand(spec));
		}

		tabLine.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
			if (newTab != null) {
				bandArea.getChildren().setAll(bandsByTab.get(newTab));
			}
		});
		if (!tabLine.getTabs().isEmpty()) {
			tabLine.getSelectionModel().selectFirst();
		}

		this.getChildren().setAll(tabLine, bandArea);
	}

	private HBox buildBand(RibbonTabSpec spec) {
		HBox band = new HBox(6);
		band.getStyleClass().add("ribbon-band");
		band.setPadding(new Insets(4, 8, 4, 8));

		List<RibbonGroup> groups = spec.groups();
		for (int i = 0; i < groups.size(); i++) {
			for (RibbonAction action : groups.get(i).actions()) {
				band.getChildren().add(buildButton(action));
			}
			// a vertical separator between groups, same as a SeparatorMenuItem between groups of
			// MenuItems within one of today's dropdown Menus - none needed after the last group
			if (i < groups.size() - 1) {
				band.getChildren().add(new Separator(Orientation.VERTICAL));
			}
		}
		return band;
	}

	private Button buildButton(RibbonAction action) {
		Button button = new Button(action.label(), new FontIcon(action.icon()));
		button.setOnAction(event -> action.action().run());
		buttonsByAction.put(action, button);
		return button;
	}

	/**
	 * The {@link Button} built for {@code action} - for callers (e.g. {@code MainView}'s
	 * disabled-while-no-repository-is-open wiring) that need to reach it after construction.
	 */
	public Button getButton(RibbonAction action) {
		return buttonsByAction.get(action);
	}

}
