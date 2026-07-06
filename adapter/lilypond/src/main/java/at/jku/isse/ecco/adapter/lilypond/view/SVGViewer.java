package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilypondCompiler;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.LilypondStringWriter;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Previews the pages of a lilypond score as SVG, rendered via {@link WebView} since JavaFX has no
 * native SVG image support. Pages are shown one at a time with Previous/Next navigation, since
 * stacking one WebView per page turned out to be unreliable (only the first would render).
 */
public class SVGViewer extends BorderPane implements ArtifactViewer {

	private static final double MIN_ZOOM = 0.25;
	private static final double MAX_ZOOM = 4.0;
	private static final double ZOOM_STEP = 1.15;

	private final LilypondStringWriter textWriter = new LilypondStringWriter();

	private final WebView webView = new WebView();
	private final Label pageLabel = new Label();
	private final Button previousButton = new Button("Previous");
	private final Button nextButton = new Button("Next");
	private final Label zoomLabel = new Label();
	private final Button zoomOutButton = new Button("−");
	private final Button zoomInButton = new Button("+");
	private final Button zoomResetButton = new Button("Reset Zoom");
	private final HBox navigationBar;

	private List<Path> pages = List.of();
	private int currentPage = 0;

	public SVGViewer() {
		previousButton.setOnAction(e -> showPage(currentPage - 1));
		nextButton.setOnAction(e -> showPage(currentPage + 1));

		zoomOutButton.setOnAction(e -> setZoom(webView.getZoom() / ZOOM_STEP));
		zoomInButton.setOnAction(e -> setZoom(webView.getZoom() * ZOOM_STEP));
		zoomResetButton.setOnAction(e -> setZoom(1.0));
		setZoom(1.0);

		// Ctrl+scroll to zoom; intercepted as a filter since WebView otherwise consumes the
		// scroll event itself for its own content scrolling.
		webView.addEventFilter(ScrollEvent.SCROLL, event -> {
			if (event.isControlDown()) {
				setZoom(webView.getZoom() * Math.pow(ZOOM_STEP, event.getDeltaY() > 0 ? 1 : -1));
				event.consume();
			}
		});

		navigationBar = new HBox(10, previousButton, pageLabel, nextButton,
				new Separator(), zoomOutButton, zoomLabel, zoomInButton, zoomResetButton);
		navigationBar.setAlignment(Pos.CENTER);
		navigationBar.setPadding(new Insets(5));
	}

	private void setZoom(double zoom) {
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
		webView.setZoom(zoom);
		zoomLabel.setText(Math.round(zoom * 100) + "%");
	}

	@Override
	public void showTree(Node node) {
		Set<Node> nodes = new HashSet<>();
		nodes.add(node);

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
			setCursor(Cursor.WAIT);
			this.setTop(null);
			this.setCenter(new ProgressIndicator());
			this.setBackground(Background.EMPTY);

			LilypondCompiler lilyC = new LilypondCompiler(this.textWriter.write(nodes)[0]);

			Thread th = new Thread(() -> {
				List<Path> compiledPages = lilyC.compileSVG();

				Platform.runLater(() -> {
					if (!compiledPages.isEmpty()) {
						this.pages = compiledPages;
						this.setTop(navigationBar);
						this.setCenter(webView);
						showPage(0);
						this.setBackground(Background.EMPTY);

					} else if (null != lilyC.getLastError()) {
						this.pages = List.of();
						this.setTop(null);
						TextArea ta = new TextArea();
						ta.setText(lilyC.getLastError());
						this.setCenter(ta);
						this.setBackground(Background.EMPTY);
					}
					setCursor(Cursor.DEFAULT);
				});
			});
			th.start();
		}
	}

	private void showPage(int index) {
		if (pages.isEmpty()) {
			return;
		}
		currentPage = Math.max(0, Math.min(index, pages.size() - 1));
		webView.getEngine().load(pages.get(currentPage).toUri().toString());
		pageLabel.setText("Page " + (currentPage + 1) + " of " + pages.size());
		previousButton.setDisable(currentPage == 0);
		nextButton.setDisable(currentPage == pages.size() - 1);
	}

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}
}
