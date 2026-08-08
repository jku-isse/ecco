package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.GraphCameraControls;
import at.jku.isse.ecco.gui.TabVisibilityAware;
import at.jku.isse.ecco.gui.view.KnowledgeGraphLayout;
import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import com.google.inject.Inject;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.camera.Camera;
import org.graphstream.ui.view.util.InteractiveElement;
import org.graphstream.ui.view.util.MouseManager;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cross-entity view of the repository: Features, Commits, Associations, and Variants, one
 * horizontal lane per entity type, plus the direct (object-reference) relationships between them -
 * see {@link KnowledgeGraphLayout} for the actual algorithm (kept separate from this class so it has
 * no JavaFX/GraphStream dependency and can be tested on its own, same split as
 * {@code FeaturesView}/{@code FeatureModelTree}). Constraints aren't a fifth lane: REQUIRES/EXCLUDES
 * render as a direct Feature -> Feature edge and MANDATORY as a marker on the Feature node itself -
 * see {@link KnowledgeGraphLayout}'s class javadoc.
 * <p>
 * Defaults to the same {@link SpringBox} force-directed layout {@code ArtifactGraphView} uses. The
 * toolbar's layout selector can also switch to a non-physics lane layout, with node positions coming
 * straight from {@link KnowledgeGraphLayout#compute} instead - see {@link #layoutMode}.
 */
public class KnowledgeGraphView extends BorderPane implements EccoListener, TabVisibilityAware {

	private final EccoService service;

	private final Graph graph;
	private FxViewer viewer;
	private FxViewPanel view;

	private final BorderPane graphContainer;
	private final ScrollBar horizontalScrollBar = new ScrollBar();
	private final ScrollBar verticalScrollBar = new ScrollBar();
	private boolean syncingScrollBars = false;
	private double contentMinX, contentMaxX, contentMinY, contentMaxY;

	/** Row 1: what's included in the graph (lane/entity filters, commit window, extra edge kinds). */
	private final ToolBar filterToolBar;
	/** Row 2: how the included graph is rendered (labels, layout, node sizing, export). */
	private final ToolBar displayToolBar;

	/**
	 * Floating association-preview panel shown on hover - same general pattern as
	 * {@code ArtifactGraphView#hoverOverlay} (plain JavaFX stacked over the GraphStream view,
	 * {@code mouseTransparent} so it never steals hover/click from the node underneath), but for an
	 * ASSOCIATION node it embeds the same per-plugin {@link AssociationInfoArtifactViewer} (Java/C/
	 * Lilypond/Text) already used by the checkout reorder dialog and Artifacts tab, showing the
	 * actual highlighted source of one representative artifact from that association, instead of
	 * just a text label. Other entity kinds (Feature/Commit/Variant) - and an ASSOCIATION with no
	 * content or no registered viewer for its plugin - fall back to the plain label, same as
	 * {@code ArtifactGraphView} always does.
	 */
	private final Label hoverInfoLabel = new Label();
	private final VBox hoverOverlay = createHoverOverlay(this.hoverInfoLabel);

	/** Guice-injected on first use (needs the repository to be initialized), not in the constructor - mirrors {@code ReorderChildrenDialog}'s lazy injection. */
	@Inject
	private Set<AssociationInfoArtifactViewer> associationInfoArtifactViewers;
	private boolean viewersInjected = false;

	private GraphicElement hoveredElement;
	private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(HOVER_DELAY_MS));
	// explicitly EventHandler<MouseEvent>-typed, rather than passing a bare method reference
	// straight to View#addListener(T, U) below - that method's generic signature gives the
	// compiler nothing to infer the functional-interface target type from otherwise (same reason
	// PanMouseManager's pressed/dragged/released are declared fields, not inline lambdas).
	private final EventHandler<MouseEvent> mouseMoved = this::handleMouseMoved;
	private final EventHandler<MouseEvent> mouseClicked = this::handleMouseClicked;

	/** See {@link ArtifactGraphView#tabVisible} for the full rationale. */
	private volatile boolean tabVisible = true;

	private boolean showLabels = DEFAULT_SHOW_LABELS;
	private final Set<KnowledgeGraphLayout.EntityKind> enabledKinds = EnumSet.allOf(KnowledgeGraphLayout.EntityKind.class);
	private int commitLimit = DEFAULT_COMMIT_LIMIT;
	private boolean includeConstraints = true;
	private boolean includeComputedVariantAssociationEdges = false;
	private boolean sizeAssociationsByArtifactCount = false;
	private boolean sizeCommitsByArtifactCount = false;

	private enum LayoutMode {
		FORCE_DIRECTED("Force-directed layout"), LANE("Lane layout");

		private final String displayName;

		LayoutMode(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return this.displayName;
		}
	}

	/** Same {@link SpringBox} class/constructor {@code ArtifactGraphView} uses; unused (never attached as a sink) in {@link LayoutMode#LANE}. */
	private final Layout forceDirectedLayout = new SpringBox(false);
	private LayoutMode layoutMode = LayoutMode.FORCE_DIRECTED;
	/** The mode the graph was actually last rendered in, so {@link #applySnapshot} can tell a mode switch from an ordinary refresh. */
	private LayoutMode lastAppliedLayoutMode = null;

	public KnowledgeGraphView(EccoService service) {
		this.service = service;

		this.filterToolBar = new ToolBar();
		this.displayToolBar = new ToolBar();
		this.setTop(new VBox(this.filterToolBar, this.displayToolBar));

		CheckBox showLabelsCheckbox = this.buildToolBar();


		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		// MultiGraph, not SingleGraph: mixing several relation kinds between entities risks more
		// than one edge landing on the same source/target pair, which SingleGraph rejects (same
		// reasoning as FeaturesView).
		this.graph = new MultiGraph("KnowledgeGraph");
		// deliberately no layout sink/enableAutoLayout(): node positions come straight from
		// KnowledgeGraphLayout.compute(), same as FeaturesView.

		this.setOnScroll(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(MIN_VIEW_PERCENT, Math.min(MAX_VIEW_PERCENT,
						view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
				this.syncScrollBarsFromCamera();
			}
		});
		this.setOnZoom(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(MIN_VIEW_PERCENT, Math.min(MAX_VIEW_PERCENT,
						view.getCamera().getViewPercent() / event.getZoomFactor())));
				this.syncScrollBarsFromCamera();
			}
		});

		showLabelsCheckbox.setSelected(this.showLabels);

		this.horizontalScrollBar.setOrientation(Orientation.HORIZONTAL);
		this.horizontalScrollBar.valueProperty().addListener((obs, oldV, newV) -> {
			if (this.syncingScrollBars || null == view) return;
			Point3 center = view.getCamera().getViewCenter();
			view.getCamera().setViewCenter(newV.doubleValue(), center.y, center.z);
		});
		this.verticalScrollBar.setOrientation(Orientation.VERTICAL);
		this.verticalScrollBar.valueProperty().addListener((obs, oldV, newV) -> {
			if (this.syncingScrollBars || null == view) return;
			Point3 center = view.getCamera().getViewCenter();
			view.getCamera().setViewCenter(center.x, this.contentMinY + this.contentMaxY - newV.doubleValue(), center.z);
		});

		Label legend = new Label("Nodes: Feature | Commit | Association | Variant (a bold outline marks an accepted-MANDATORY feature).  "
				+ "Edges: touches (commit->association) | selects (commit/variant->feature) | involves (association->feature) | "
				+ "requires/excludes (feature->feature, only shown when Show constraints is enabled) | produces (commit->variant) | "
				+ "touches, computed (variant->association, only shown when enabled above).");
		legend.setWrapText(true);
		legend.setPadding(new Insets(4, 8, 4, 8));
		legend.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");

		this.graphContainer = new BorderPane();
		this.graphContainer.setTop(legend);
		this.graphContainer.setBottom(horizontalScrollBar);
		this.graphContainer.setRight(verticalScrollBar);
		this.setCenter(this.graphContainer);

		service.addListener(this);
		Platform.runLater(() -> statusChangedEvent(service));
	}

	private void setToolBarsDisabled(boolean disabled) {
		this.filterToolBar.setDisable(disabled);
		this.displayToolBar.setDisable(disabled);
	}

	/**
	 * Builds the two toolbar rows, grouped by what they actually do rather than the order features
	 * were added in:
	 * <ul>
	 * <li>{@link #filterToolBar} - what's included in the graph: which entity-kind lanes are shown,
	 * how many commits, and the two opt-in extra edge kinds (constraints, computed variant edges).</li>
	 * <li>{@link #displayToolBar} - how the included graph is rendered: labels, layout mode, the two
	 * artifact-count node-sizing toggles, and Export.</li>
	 * </ul>
	 * Split out of the constructor purely for readability, same as {@code ArtifactGraphView.buildToolBar()}.
	 * Returns the Show Labels checkbox so the constructor can set its initial value once
	 * {@link #graph} exists (see the comment there for why that ordering matters).
	 */
	private CheckBox buildToolBar() {
		for (KnowledgeGraphLayout.EntityKind kind : KnowledgeGraphLayout.EntityKind.values()) {
			CheckBox kindCheckbox = new CheckBox(displayName(kind));
			kindCheckbox.setSelected(true);
			kindCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
				if (newVal) {
					KnowledgeGraphView.this.enabledKinds.add(kind);
				} else {
					KnowledgeGraphView.this.enabledKinds.remove(kind);
				}
				KnowledgeGraphView.this.refreshGraph();
			});
			this.filterToolBar.getItems().add(kindCheckbox);
		}
		this.filterToolBar.getItems().add(new Separator());

		Label commitLimitLabel = new Label("Commits shown (0 = all): ");
		Spinner<Integer> commitLimitSpinner = new EditableSpinner(0, COMMIT_LIMIT_MAX, commitLimit);
		commitLimitSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			KnowledgeGraphView.this.commitLimit = newValue;
			KnowledgeGraphView.this.refreshGraph();
		});
		this.filterToolBar.getItems().addAll(commitLimitLabel, commitLimitSpinner, new Separator());

		CheckBox constraintsCheckbox = new CheckBox("Show constraints");
		constraintsCheckbox.setSelected(this.includeConstraints);
		constraintsCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
			KnowledgeGraphView.this.includeConstraints = newVal;
			KnowledgeGraphView.this.refreshGraph();
		});
		this.filterToolBar.getItems().add(constraintsCheckbox);

		CheckBox computedEdgesCheckbox = new CheckBox("Show variant -> association edges (computed)");
		computedEdgesCheckbox.setSelected(false);
		computedEdgesCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
			KnowledgeGraphView.this.includeComputedVariantAssociationEdges = newVal;
			KnowledgeGraphView.this.refreshGraph();
		});
		this.filterToolBar.getItems().add(computedEdgesCheckbox);

		CheckBox showLabelsCheckbox = new CheckBox("Show Labels");
		showLabelsCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
			KnowledgeGraphView.this.showLabels = newVal;
			KnowledgeGraphView.this.updateGraphStylesheet();
		});
		this.displayToolBar.getItems().add(showLabelsCheckbox);

		Label layoutLabel = new Label("Layout: ");
		ChoiceBox<LayoutMode> layoutChoice = new ChoiceBox<>();
		layoutChoice.getItems().setAll(LayoutMode.values());
		layoutChoice.setValue(this.layoutMode);
		layoutChoice.valueProperty().addListener((obs, oldValue, newValue) -> {
			KnowledgeGraphView.this.layoutMode = newValue;
			KnowledgeGraphView.this.refreshGraph();
		});
		this.displayToolBar.getItems().addAll(new Separator(), layoutLabel, layoutChoice, new Separator());

		CheckBox sizeAssociationsCheckbox = new CheckBox("Size associations by artifact count");
		sizeAssociationsCheckbox.setSelected(this.sizeAssociationsByArtifactCount);
		sizeAssociationsCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
			KnowledgeGraphView.this.sizeAssociationsByArtifactCount = newVal;
			KnowledgeGraphView.this.refreshGraph();
		});
		this.displayToolBar.getItems().add(sizeAssociationsCheckbox);

		CheckBox sizeCommitsCheckbox = new CheckBox("Size commits by artifact count");
		sizeCommitsCheckbox.setSelected(this.sizeCommitsByArtifactCount);
		sizeCommitsCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
			KnowledgeGraphView.this.sizeCommitsByArtifactCount = newVal;
			KnowledgeGraphView.this.refreshGraph();
		});
		this.displayToolBar.getItems().add(sizeCommitsCheckbox);

		this.displayToolBar.getItems().add(new Separator());
		this.displayToolBar.getItems().addAll(GraphCameraControls.build(() -> this.view, MIN_VIEW_PERCENT, MAX_VIEW_PERCENT, this::syncScrollBarsFromCamera));

		Button exportButton = new Button("Export");
		exportButton.setOnAction(ae -> {
			this.setToolBarsDisabled(true);

			FileChooser fileChooser = new FileChooser();
			// FileSinkFactory.sinkFor() picks a writer purely off the file extension and returns
			// null (see the "Unknown file extension" branch below) for anything it doesn't
			// recognize - the save dialog has no extension of its own to fall back on otherwise,
			// so list the formats GraphStream actually supports and default to one that opens
			// cleanly in most external tools.
			fileChooser.getExtensionFilters().addAll(
					new FileChooser.ExtensionFilter("GraphML (*.graphml)", "*.graphml"),
					new FileChooser.ExtensionFilter("GEXF (*.gexf)", "*.gexf"),
					new FileChooser.ExtensionFilter("DOT (*.dot)", "*.dot"),
					new FileChooser.ExtensionFilter("DGS (*.dgs)", "*.dgs"));
			fileChooser.setInitialFileName("knowledge-graph.graphml");
			File selectedFile = fileChooser.showSaveDialog(KnowledgeGraphView.this.getScene().getWindow());

			if (selectedFile != null) {
				FileSink out = FileSinkFactory.sinkFor(selectedFile.toString());
				if (out != null) {
					try {
						out.writeAll(KnowledgeGraphView.this.graph, selectedFile.toString());
						out.flush();
					} catch (IOException e) {
						new ExceptionAlert(e).show();
					}
				} else {
					Alert alert = new Alert(Alert.AlertType.ERROR);
					alert.setHeaderText("Unknown file extension.");
					alert.setContentText("Unknown file extension.");
					alert.show();
				}
			}

			this.setToolBarsDisabled(false);
		});
		this.displayToolBar.getItems().addAll(new Separator(), exportButton);

		return showLabelsCheckbox;
	}

	private static String displayName(KnowledgeGraphLayout.EntityKind kind) {
		String name = kind.name();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase() + "s";
	}

	/**
	 * The two layout modes need genuinely different node styling, not just different positions:
	 * {@link LayoutMode#LANE}'s box shape sized in "gu" (graph units, so its footprint stays fixed
	 * relative to the layout's own coordinate space - see {@code FeaturesView}'s identical
	 * reasoning) is enormous relative to {@link SpringBox}'s physics scale, which - per
	 * {@code ArtifactGraphView}'s own proven-working circle nodes - is tuned for small, "px" (fixed
	 * on-screen pixel size regardless of zoom) shapes. Using the LANE box size in FORCE_DIRECTED
	 * mode was the real cause of a node rendering as one screen-filling blob: every node's box was
	 * ~100x wider than the tiny area SpringBox actually spreads nodes across, so the boxes
	 * overlapped completely regardless of how well the physics itself had settled.
	 */
	/**
	 * Maps an artifact count (an association's own, or a commit's total across every association it
	 * touches) to a node size, normalized against the largest value *of the same kind* currently in
	 * view (not some fixed artifacts-per-pixel scale) - repos vary wildly in size, so a relative scale
	 * is what actually stays legible: the biggest node of that kind in the current snapshot always
	 * renders at the mode's max size, the smallest (or an empty one) at its min. Normalized per kind
	 * rather than across both together, since a commit's count is a sum over potentially many
	 * associations and would otherwise dwarf every individual association's own count on the same
	 * scale. Scaled by square root, not linearly, since node "size" here reads visually as area (a
	 * circle in FORCE_DIRECTED mode, a square in LANE mode) - linear scaling by count would make area
	 * grow with the *square* of count, exaggerating differences far beyond what the numbers actually mean.
	 */
	private String sizedNodeSize(int artifactCount, int maxArtifactCount) {
		double minSize = this.layoutMode == LayoutMode.LANE ? SIZED_NODE_MIN_LANE : SIZED_NODE_MIN_FORCE_DIRECTED;
		double maxSize = this.layoutMode == LayoutMode.LANE ? SIZED_NODE_MAX_LANE : SIZED_NODE_MAX_FORCE_DIRECTED;
		String unit = this.layoutMode == LayoutMode.LANE ? "gu" : "px";
		double t = maxArtifactCount > 0 ? Math.sqrt(Math.max(0, artifactCount)) / Math.sqrt(maxArtifactCount) : 0;
		double size = minSize + t * (maxSize - minSize);
		return size + unit;
	}

	private void updateGraphStylesheet() {
		String textMode = this.showLabels ? "text-mode: normal; " : "text-mode: hidden; ";
		StringBuilder css = new StringBuilder();
		if (this.layoutMode == LayoutMode.LANE) {
			css.append("node { ").append(textMode)
					.append("text-background-mode: plain; shape: box; size: ").append(NODE_WIDTH).append("gu, ").append(NODE_HEIGHT)
					.append("gu; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
		} else {
			css.append("node { ").append(textMode)
					.append("text-background-mode: plain; shape: circle; size: ").append(FORCE_DIRECTED_NODE_SIZE)
					.append("px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
		}
		for (KnowledgeGraphLayout.EntityKind kind : KnowledgeGraphLayout.EntityKind.values()) {
			css.append("node.").append(kind.name().toLowerCase()).append(" { fill-color: ")
					.append(toHexColor(CategoricalColorPalette.colorAt(nodeColorIndex(kind)))).append("; } ");
		}
		// same fill as a plain feature node, just a bold outline - see the class javadoc on why
		// MANDATORY is a Feature-node decoration here rather than its own node or a self-loop edge.
		css.append("node.featuremandatory { fill-color: ")
				.append(toHexColor(CategoricalColorPalette.colorAt(nodeColorIndex(KnowledgeGraphLayout.EntityKind.FEATURE))))
				.append("; stroke-width: 3px; } ");
		if (this.sizeAssociationsByArtifactCount) {
			// "dyn-size" tells GraphStream to read each element's own "ui.size" attribute instead of
			// the static "size" set on the generic node rule above - applySnapshot() sets that
			// attribute per association node, scaled by artifact count, only when this is on.
			css.append("node.association { size-mode: dyn-size; } ");
		}
		if (this.sizeCommitsByArtifactCount) {
			css.append("node.commit { size-mode: dyn-size; } ");
		}
		css.append("edge { ").append(textMode).append(" size: 1px; arrow-size: 6px, 4px; } ");
		for (Map.Entry<KnowledgeGraphLayout.EdgeKind, String> entry : EDGE_COLORS.entrySet()) {
			css.append("edge.").append(entry.getKey().name().toLowerCase()).append(" { fill-color: ").append(entry.getValue()).append("; } ");
		}
		this.graph.setAttribute("ui.stylesheet", css.toString());
	}

	private void initView() {
		closeView();
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel) viewer.addDefaultView(false, new FxGraphRenderer());
		// replaces the default manager's node-drag behavior with plain click-drag panning, same
		// reasoning as FeaturesView.PanMouseManager - node positions come straight from
		// KnowledgeGraphLayout and dragging one would visually detach it from its meaning.
		view.setMouseManager(new PanMouseManager());
		// independent of PanMouseManager (which only listens for PRESSED/DRAGGED/RELEASED) - View
		// supports any number of listeners per event type, so this coexists without touching the
		// panning behavior at all. Deliberately plain MOUSE_MOVED + PauseTransition instead of
		// GraphStream's own FxMouseOverMouseManager (used by ArtifactGraphView): that class's
		// press/drag handling defaults to node-dragging/rubber-band-selection, which would fight
		// PanMouseManager's click-drag-to-pan the moment it's mixed in via inheritance.
		view.addListener(MouseEvent.MOUSE_MOVED, this.mouseMoved);
		// clicking an association tears its preview off into its own floating, resizable window -
		// same hit-testing as the hover hand-off below, just on a different event type, so it coexists
		// with both PanMouseManager and the hover listener without any of them needing to know about it.
		view.addListener(MouseEvent.MOUSE_CLICKED, this.mouseClicked);

		StackPane stackPane = new StackPane(view, this.hoverOverlay);
		StackPane.setAlignment(this.hoverOverlay, Pos.TOP_LEFT);
		this.graphContainer.setCenter(stackPane);
	}

	private void closeView() {
		this.hoverOverlay.setVisible(false);
		this.hoveredElement = null;
		this.hoverDelay.stop();

		if (null == viewer) {
			return;
		}

		this.graphContainer.setCenter(new Pane());
		try {
			viewer.close();
		} catch (Exception ignored) {
			// see ArtifactGraphView.closeView() for why this is safely ignorable
		}
		view = null;
		viewer = null;
	}

	/** Click-drag panning; see {@code FeaturesView.PanMouseManager} for the full rationale (identical here). */
	private final class PanMouseManager implements MouseManager {

		private View managedView;
		private double lastPxX, lastPxY;

		private final EventHandler<MouseEvent> pressed = event -> {
			this.managedView.requireFocus();
			this.lastPxX = event.getX();
			this.lastPxY = event.getY();
		};

		private final EventHandler<MouseEvent> dragged = event -> {
			Camera camera = this.managedView.getCamera();
			Point3 from = camera.transformPxToGu(this.lastPxX, this.lastPxY);
			Point3 to = camera.transformPxToGu(event.getX(), event.getY());
			Point3 center = camera.getViewCenter();
			camera.setViewCenter(center.x - (to.x - from.x), center.y - (to.y - from.y), center.z);
			this.lastPxX = event.getX();
			this.lastPxY = event.getY();
			KnowledgeGraphView.this.syncScrollBarsFromCamera();
		};

		private final EventHandler<MouseEvent> released = event -> {
		};

		@Override
		public void init(GraphicGraph graph, View view) {
			this.managedView = view;
			view.addListener(MouseEvent.MOUSE_PRESSED, pressed);
			view.addListener(MouseEvent.MOUSE_DRAGGED, dragged);
			view.addListener(MouseEvent.MOUSE_RELEASED, released);
		}

		@Override
		public void release() {
			this.managedView.removeListener(MouseEvent.MOUSE_PRESSED, pressed);
			this.managedView.removeListener(MouseEvent.MOUSE_DRAGGED, dragged);
			this.managedView.removeListener(MouseEvent.MOUSE_RELEASED, released);
		}

		@Override
		public EnumSet<InteractiveElement> getManagedTypes() {
			return EnumSet.noneOf(InteractiveElement.class);
		}
	}

	/** Small dark tooltip chrome for the plain-label case (Feature/Commit/Variant nodes, or an ASSOCIATION with nothing previewable). */
	private static final String TEXT_OVERLAY_STYLE = "-fx-background-color: rgba(20,20,20,0.9); -fx-padding: 6px 10px; " +
			"-fx-background-radius: 4px; -fx-border-color: rgba(255,255,255,0.3); -fx-border-radius: 4px;";
	/** Lightweight panel chrome for an embedded code preview - a thin border instead of a filled dark background, which read as a thick black margin around the preview's own light background. */
	private static final String PREVIEW_OVERLAY_STYLE = "-fx-background-color: white; -fx-padding: 1px; " +
			"-fx-background-radius: 3px; -fx-border-color: rgba(0,0,0,0.25); -fx-border-width: 1px; -fx-border-radius: 3px;";

	private static VBox createHoverOverlay(Label infoLabel) {
		VBox overlay = new VBox(infoLabel);
		overlay.setMouseTransparent(true);
		overlay.setVisible(false);
		// StackPane resizes children to fill its own bounds by default - without this, the overlay
		// stretched to the size of the whole graph view instead of sizing to its content.
		overlay.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
		overlay.setStyle(TEXT_OVERLAY_STYLE);
		infoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
		infoLabel.setWrapText(false);
		return overlay;
	}

	/**
	 * Mouse-moved handler for the hover-preview overlay - deliberately independent of
	 * {@link PanMouseManager} (see the comment at its registration in {@link #initView}). Debounces
	 * via {@link #hoverDelay} (a plain FX-thread {@link PauseTransition}, unlike {@code
	 * ArtifactGraphView}'s copy of GraphStream's own raw-{@code java.util.Timer}-based hover manager)
	 * so darting the mouse across many nodes doesn't build a preview for every one passed over.
	 */
	private void handleMouseMoved(MouseEvent event) {
		if (this.view == null) return;

		GraphicElement current = this.view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), event.getX(), event.getY());
		if (Objects.equals(current, this.hoveredElement)) {
			return;
		}

		this.hoverDelay.stop();
		this.hoveredElement = current;
		this.hideHoverOverlay();

		if (current != null) {
			this.hoverDelay.setOnFinished(e -> {
				if (Objects.equals(current, this.hoveredElement)) {
					this.showHoverOverlay(current);
				}
			});
			this.hoverDelay.playFromStart();
		}
	}

	/**
	 * Click counterpart to {@link #handleMouseMoved}: tears an ASSOCIATION node's preview off into its
	 * own floating window via {@link #openDetachedPreview}. Fires natively only on a genuine click
	 * (press+release without an intervening drag past JavaFX's own threshold), so it never fights
	 * {@link PanMouseManager}'s click-drag panning - a pan just never generates a MOUSE_CLICKED.
	 */
	private void handleMouseClicked(MouseEvent event) {
		if (this.view == null) return;

		GraphicElement element = this.view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), event.getX(), event.getY());
		if (element == null) return;

		boolean isAssociation = "association".equals(element.hasAttribute("ui.class") ? element.getAttribute("ui.class") : null);
		if (!isAssociation) return;

		this.openDetachedPreview(element.getId().substring(2));
	}

	/**
	 * Opens the given association's preview in its own floating, resizable, non-modal window. Uses a
	 * *fresh* {@link AssociationInfoArtifactViewer} instance straight from Guice, deliberately not the
	 * shared one {@link #buildAssociationPreview} reuses for hovering - a JavaFX node can only live in
	 * one place in the scene graph at a time, and this window needs to keep showing its content even
	 * after the mouse moves elsewhere (or the same viewer type gets hovered again). Each click opens
	 * another independent window, so several associations can be compared side by side.
	 */
	private void openDetachedPreview(String associationId) {
		if (!this.ensureViewersInjected()) return;

		Association association = this.findAssociationById(associationId);
		if (association == null || association.getRootNode() == null) return;

		at.jku.isse.ecco.tree.Node contentNode = findRepresentativeContentNode(association.getRootNode());
		if (contentNode == null) return;

		String pluginId = ArtifactDetailView.getPluginId(contentNode);
		if (pluginId == null) return;

		AssociationInfoArtifactViewer template = null;
		for (AssociationInfoArtifactViewer candidate : this.associationInfoArtifactViewers) {
			if (candidate instanceof Pane && pluginId.equals(candidate.getPluginId())) {
				template = candidate;
				break;
			}
		}
		if (template == null) return;

		AssociationInfoArtifactViewer detached = (AssociationInfoArtifactViewer) this.service.getInjector().getInstance(template.getClass());
		detached.setAssociationInfos(null);
		// unlike the passive hover preview, this is a real window with room to spare - worth showing
		// the per-line details panel here (left at its interface-default "on" state).
		detached.showTree(contentNode);

		at.jku.isse.ecco.tree.Node fileNode = enclosingFileNode(contentNode);
		PreviewScan scan = scanFileNode(fileNode);
		Pane pane = (Pane) detached;
		pane.setPrefSize(previewWidthFor(scan), previewHeightFor(scan));

		Stage stage = new Stage();
		stage.setTitle("Association Preview (" + pluginId + ")");
		if (this.getScene() != null) {
			stage.initOwner(this.getScene().getWindow());
		}
		stage.setScene(new Scene(pane));
		stage.setResizable(true);
		stage.show();
	}

	/**
	 * Fills in and positions {@link #hoverOverlay} for the given node, then makes it visible. For an
	 * ASSOCIATION node with previewable content, swaps in the embedded {@link
	 * AssociationInfoArtifactViewer} from {@link #buildAssociationPreview}; otherwise falls back to
	 * the plain text label, same as {@code ArtifactGraphView} always does.
	 */
	private void showHoverOverlay(GraphicElement element) {
		if (this.view == null) return;

		// "ui.class" is set in applySnapshot() to placement.kind.name().toLowerCase() (or
		// "featuremandatory") - checking it, rather than parsing the "A:"/"F:"/... id prefix
		// KnowledgeGraphLayout.compute() happens to use, keys off the same semantic tag the
		// stylesheet colors nodes by, not an incidental string format.
		Object uiClass = element.hasAttribute("ui.class") ? element.getAttribute("ui.class") : null;
		Pane preview;
		if ("association".equals(uiClass)) {
			preview = this.buildAssociationPreview(element.getId().substring(2));
		} else if ("commit".equals(uiClass)) {
			preview = this.buildCommitPreview(element.getId().substring(2));
		} else {
			preview = null;
		}

		if (preview != null) {
			// dark tooltip chrome (below) reads fine behind a couple of words of plain white text,
			// but around a code preview it showed as a thick black margin wherever the preview's own
			// (white/light) background didn't fully cover it - a lightweight border reads as a panel
			// instead.
			this.hoverOverlay.setStyle(PREVIEW_OVERLAY_STYLE);
			this.hoverOverlay.getChildren().setAll(preview);
		} else {
			this.hoverOverlay.setStyle(TEXT_OVERLAY_STYLE);
			Object label = element.hasAttribute("label") ? element.getAttribute("label") : null;
			this.hoverInfoLabel.setText(label != null ? label.toString() : element.getId());
			this.hoverOverlay.getChildren().setAll(this.hoverInfoLabel);
		}

		Point3 pixelPos = this.view.getCamera().transformGuToPx(element.getX(), element.getY(), element.getZ());
		double overlayWidth = this.hoverOverlay.prefWidth(-1);
		double overlayHeight = this.hoverOverlay.prefHeight(-1);
		double containerWidth = this.graphContainer.getWidth();
		double containerHeight = this.graphContainer.getHeight();
		// Anchoring purely at pixelPos + a fixed offset (the old behavior) let a tall/wide preview run
		// off the bottom/right edge of the graph view with nothing clipping it back into view - it just
		// rendered past the window edge, so only its top few lines were ever visible. Clamp so the whole
		// box stays on screen, flipping to the other side of the cursor when there isn't room below/right.
		double x = pixelPos.x + HOVER_OVERLAY_OFFSET;
		if (containerWidth > 0 && x + overlayWidth > containerWidth) {
			x = pixelPos.x - HOVER_OVERLAY_OFFSET - overlayWidth;
		}
		double y = pixelPos.y + HOVER_OVERLAY_OFFSET;
		if (containerHeight > 0 && y + overlayHeight > containerHeight) {
			y = pixelPos.y - HOVER_OVERLAY_OFFSET - overlayHeight;
		}
		this.hoverOverlay.setTranslateX(Math.max(0, x));
		this.hoverOverlay.setTranslateY(Math.max(0, y));
		this.hoverOverlay.setVisible(true);
	}

	private void hideHoverOverlay() {
		this.hoverOverlay.setVisible(false);
	}

	private Association findAssociationById(String associationId) {
		for (Association candidate : this.service.getRepository().getAssociations()) {
			if (associationId.equals(candidate.getId())) return candidate;
		}
		return null;
	}

	private Commit findCommitById(String commitId) {
		for (Commit candidate : this.service.getRepository().getCommits()) {
			if (commitId.equals(candidate.getId())) return candidate;
		}
		return null;
	}

	/**
	 * Commit counterpart to {@link #buildAssociationPreview} - there's no per-plugin viewer for a
	 * commit (it isn't code), so this just lays out its own message/committer/date fields as plain
	 * labels rather than reusing an {@link AssociationInfoArtifactViewer}. Width is still
	 * content-driven (same {@link #measureTextWidth} used for association previews), clamped so a
	 * one-line commit message doesn't get a huge box and a long one wraps instead of running off-screen.
	 */
	private Pane buildCommitPreview(String commitId) {
		Commit commit = this.findCommitById(commitId);
		if (commit == null) return null;

		String message = commit.getCommitMessage() != null ? commit.getCommitMessage() : "";
		String committerText = "Committer: " + (commit.getUsername() != null ? commit.getUsername() : "?");
		String dateText = "Date: " + (commit.getDate() != null ? COMMIT_DATE_FORMAT.format(commit.getDate()) : "?");

		Label messageLabel = new Label(message);
		messageLabel.setStyle("-fx-font-weight: bold;");
		Label committerLabel = new Label(committerText);
		Label dateLabel = new Label(dateText);

		double width = Math.max(COMMIT_PREVIEW_MIN_WIDTH, Math.min(COMMIT_PREVIEW_MAX_WIDTH,
				Math.max(measureTextWidth(message), Math.max(measureTextWidth(committerText), measureTextWidth(dateText)))
						+ COMMIT_PREVIEW_WIDTH_PADDING));
		messageLabel.setWrapText(true);
		messageLabel.setMaxWidth(width - COMMIT_PREVIEW_WIDTH_PADDING);

		VBox box = new VBox(4, messageLabel, committerLabel, dateLabel);
		box.setPadding(new Insets(6));
		box.setPrefWidth(width);
		box.setMaxWidth(width);
		return box;
	}

	/**
	 * Finds the real {@link Association} by id, picks one representative content artifact from its
	 * tree (an association can span multiple files; this deliberately previews just one - see the
	 * design discussion this feature started from), and returns the matching registered {@link
	 * AssociationInfoArtifactViewer} (Java/C/Lilypond/Text) showing/highlighting it - or {@code null}
	 * if the association has no content, or no viewer is registered for its plugin.
	 */
	private Pane buildAssociationPreview(String associationId) {
		if (!this.ensureViewersInjected()) return null;

		Association association = this.findAssociationById(associationId);
		if (association == null || association.getRootNode() == null) return null;

		at.jku.isse.ecco.tree.Node contentNode = findRepresentativeContentNode(association.getRootNode());
		if (contentNode == null) return null;

		String pluginId = ArtifactDetailView.getPluginId(contentNode);
		if (pluginId == null) return null;

		for (AssociationInfoArtifactViewer viewer : this.associationInfoArtifactViewers) {
			if (viewer instanceof Pane && pluginId.equals(viewer.getPluginId())) {
				// association-selection colors are Artifacts-tab state, unrelated to hovering here -
				// null just means every line/token renders in its adapter's default (unselected) look.
				viewer.setAssociationInfos(null);
				// this preview is passive (nothing will hover-within-the-hover), so the per-line
				// details panel Java/C/Lilypond's viewers show below the code can never populate -
				// omit it rather than reserve dead space for it.
				viewer.setShowDetailsPanel(false);
				viewer.showTree(contentNode);
				Pane pane = (Pane) viewer;
				at.jku.isse.ecco.tree.Node fileNode = enclosingFileNode(contentNode);
				PreviewScan scan = scanFileNode(fileNode);
				pane.setPrefSize(previewWidthFor(scan), previewHeightFor(scan));
				return pane;
			}
		}
		return null;
	}

	/** Walks up from a content node to its enclosing file node ({@link PluginArtifactData}), the shared starting point for both preview-size measurements below. */
	private static at.jku.isse.ecco.tree.Node enclosingFileNode(at.jku.isse.ecco.tree.Node contentNode) {
		at.jku.isse.ecco.tree.Node fileNode = contentNode;
		while (fileNode != null && !(fileNode.getArtifact() != null && fileNode.getArtifact().getData() instanceof PluginArtifactData)) {
			fileNode = fileNode.getParent();
		}
		return fileNode;
	}

	/** Result of {@link #scanFileNode}: what's needed to size the preview, gathered in one bounded walk. */
	private static final class PreviewScan {
		double maxTextWidth;
		int leafCount;
	}

	/**
	 * Scans every genuine content node under {@code fileNode} - not just its direct children. For a
	 * flat adapter (text) direct children already are the rendered rows, but Java/C/Lilypond nest
	 * content arbitrarily deep (statements inside methods, tokens inside context blocks), so counting
	 * only direct children massively undercounts how many rows those viewers actually render; walking
	 * every content leaf is what actually tracks "how much is being shown". Bounded so a huge file
	 * doesn't cost a huge scan - the preview only ever shows a handful of rows anyway.
	 */
	private static PreviewScan scanFileNode(at.jku.isse.ecco.tree.Node fileNode) {
		PreviewScan scan = new PreviewScan();
		if (fileNode != null) scanContentNode(fileNode, scan);
		return scan;
	}

	private static void scanContentNode(at.jku.isse.ecco.tree.Node node, PreviewScan scan) {
		if (scan.leafCount >= HOVER_PREVIEW_SCAN_LIMIT) return;
		ArtifactData data = node.getArtifact() != null ? node.getArtifact().getData() : null;
		if (data != null && !(data instanceof DirectoryArtifactData) && !(data instanceof PluginArtifactData)) {
			scan.leafCount++;
			scan.maxTextWidth = Math.max(scan.maxTextWidth, measureTextWidth(String.valueOf(node.getArtifact())));
		}
		for (at.jku.isse.ecco.tree.Node child : node.getChildren()) {
			if (scan.leafCount >= HOVER_PREVIEW_SCAN_LIMIT) break;
			scanContentNode(child, scan);
		}
	}

	/**
	 * "Only as small as needed": sized off the scanned content-leaf count (one row per leaf, matching
	 * every viewer's shared {@code fixedCellSize(20)}) rather than a constant size - a three-line
	 * association gets a three-line-tall preview, not the same box as a forty-line one. Still clamped
	 * to a sane range, since a huge file shouldn't produce a huge popup.
	 */
	private static double previewHeightFor(PreviewScan scan) {
		int lineCount = Math.max(1, scan.leafCount);
		return Math.max(HOVER_PREVIEW_MIN_HEIGHT, Math.min(HOVER_PREVIEW_MAX_HEIGHT, lineCount * HOVER_PREVIEW_LINE_HEIGHT));
	}

	/**
	 * Same "only as small as needed" idea, applied to width: uses the widest rendered line of text seen
	 * during the scan, plus padding for the list's own insets/scrollbar. Clamped to a sane range - a
	 * one-word line doesn't get a sliver-width preview, and a very long line doesn't get a huge one.
	 */
	private static double previewWidthFor(PreviewScan scan) {
		return Math.max(HOVER_PREVIEW_MIN_WIDTH, Math.min(HOVER_PREVIEW_MAX_WIDTH, scan.maxTextWidth + HOVER_PREVIEW_WIDTH_PADDING));
	}

	private static double measureTextWidth(String text) {
		if (text == null || text.isEmpty()) return 0;
		Text measuring = new Text(text);
		measuring.setFont(Font.getDefault());
		return measuring.getLayoutBounds().getWidth();
	}

	/** Lazy, one-time Guice injection - mirrors {@code ReorderChildrenDialog}'s identical pattern (needs the repository/injector to exist first). */
	private boolean ensureViewersInjected() {
		if (this.viewersInjected) return this.associationInfoArtifactViewers != null;
		if (!this.service.isInitialized()) return false;
		this.service.getInjector().injectMembers(this);
		this.viewersInjected = true;
		return this.associationInfoArtifactViewers != null;
	}

	/** Pre-order search for the first genuine content artifact (a line, token, statement, ...) under {@code node} - skips directory/file structural nodes, which have nothing to preview themselves. */
	private static at.jku.isse.ecco.tree.Node findRepresentativeContentNode(at.jku.isse.ecco.tree.Node node) {
		ArtifactData data = node.getArtifact() != null ? node.getArtifact().getData() : null;
		if (data != null && !(data instanceof DirectoryArtifactData) && !(data instanceof PluginArtifactData)) {
			return node;
		}
		for (at.jku.isse.ecco.tree.Node child : node.getChildren()) {
			at.jku.isse.ecco.tree.Node found = findRepresentativeContentNode(child);
			if (found != null) return found;
		}
		return null;
	}

	/** Builds a fresh snapshot and queues it for rendering; see {@code ArtifactGraphView#refreshGraph()}. */
	private void refreshGraph() {
		this.refreshGraph(this.buildSnapshot());
	}

	private void refreshGraph(KnowledgeGraphLayout.Snapshot snapshot) {
		if (this.viewer == null || this.view == null) {
			return;
		}

		this.setToolBarsDisabled(true);
		SwingUtilities.invokeLater(() -> {
			if (this.viewer == null || this.view == null) {
				return;
			}
			this.applySnapshot(snapshot);
			Platform.runLater(() -> this.setToolBarsDisabled(false));
		});
	}

	/**
	 * Walks the repository via {@link KnowledgeGraphLayout#compute}. Touches only the ecco data
	 * model (no GraphStream/Swing/JavaFX), so it's safe to call synchronously from any thread -
	 * notably the commit thread itself, from {@link #statusChangedEvent} - see
	 * {@code ArtifactGraphView#buildSnapshot()} for the full rationale.
	 */
	private KnowledgeGraphLayout.Snapshot buildSnapshot() {
		return KnowledgeGraphLayout.compute(this.service.getRepository(), Set.copyOf(this.enabledKinds),
				this.commitLimit, this.includeConstraints, this.includeComputedVariantAssociationEdges);
	}

	/**
	 * Renders a previously-built snapshot into the live GraphStream graph. Swing-thread only. Same
	 * flicker-avoidance (view hidden during rebuild) and camera-reset-only-on-first-render as
	 * {@code ArtifactGraphView}/{@code FeaturesView}.
	 * <p>
	 * {@link #forceDirectedLayout} is unconditionally detached/cleared before the rebuild and only
	 * re-attached afterwards if {@link #layoutMode} is {@link LayoutMode#FORCE_DIRECTED} - mirrors
	 * {@code ArtifactGraphView#applySnapshot}'s own always-detach-then-rebuild-then-reattach
	 * sequence (proven safe there to call unconditionally, including when the sink was never
	 * attached in the first place). In {@link LayoutMode#LANE}, node positions are set explicitly
	 * from the snapshot instead, and physics-only state (like {@link Layout#clear()}) doesn't apply.
	 */
	private void applySnapshot(KnowledgeGraphLayout.Snapshot snapshot) {
		assert viewer != null && view != null;

		boolean firstRender = this.graph.getNodeCount() == 0;

		this.view.setVisible(false);
		try {
			this.viewer.disableAutoLayout();
			this.graph.removeSink(this.forceDirectedLayout);
			this.forceDirectedLayout.removeAttributeSink(this.graph);
			this.forceDirectedLayout.clear();
			this.graph.clear();

			this.graph.setAttribute("ui.quality");
			this.graph.setAttribute("ui.antialias");

			// Association/commit counts live on completely different scales (a commit's count is a sum
			// across potentially many associations) - normalized separately per kind, see sizedNodeSize().
			int maxAssociationArtifactCount = 0;
			int maxCommitArtifactCount = 0;
			for (KnowledgeGraphLayout.Placement placement : snapshot.nodes) {
				if (this.sizeAssociationsByArtifactCount && placement.kind == KnowledgeGraphLayout.EntityKind.ASSOCIATION) {
					maxAssociationArtifactCount = Math.max(maxAssociationArtifactCount, placement.artifactCount);
				} else if (this.sizeCommitsByArtifactCount && placement.kind == KnowledgeGraphLayout.EntityKind.COMMIT) {
					maxCommitArtifactCount = Math.max(maxCommitArtifactCount, placement.artifactCount);
				}
			}

			for (KnowledgeGraphLayout.Placement placement : snapshot.nodes) {
				Node graphNode = this.graph.addNode(placement.id);
				boolean hasArtifactCount = (placement.kind == KnowledgeGraphLayout.EntityKind.ASSOCIATION
						|| placement.kind == KnowledgeGraphLayout.EntityKind.COMMIT) && placement.artifactCount >= 0;
				String label = hasArtifactCount ? placement.label + " (" + placement.artifactCount + ")" : placement.label;
				graphNode.setAttribute("label", label);
				if (this.layoutMode == LayoutMode.LANE) {
					graphNode.setAttribute("xyz", placement.x, placement.y, 0.0);
				}
				// FORCE_DIRECTED: deliberately leave "xyz" unset, exactly like ArtifactGraphView's
				// own SpringBox-driven nodes - SpringBox assigns its own internal initial
				// placement to unpositioned nodes, already calibrated to its own physics scale.
				graphNode.setAttribute("ui.class", placement.mandatory ? "featuremandatory" : placement.kind.name().toLowerCase());
				if (this.sizeAssociationsByArtifactCount && placement.kind == KnowledgeGraphLayout.EntityKind.ASSOCIATION) {
					graphNode.setAttribute("ui.size", sizedNodeSize(placement.artifactCount, maxAssociationArtifactCount));
				} else if (this.sizeCommitsByArtifactCount && placement.kind == KnowledgeGraphLayout.EntityKind.COMMIT) {
					graphNode.setAttribute("ui.size", sizedNodeSize(placement.artifactCount, maxCommitArtifactCount));
				}
			}
			for (KnowledgeGraphLayout.Edge edge : snapshot.edges) {
				this.graph.addEdge(edge.id, edge.sourceId, edge.targetId, true)
						.setAttribute("ui.class", edge.kind.name().toLowerCase());
			}

			this.updateGraphStylesheet();

			if (this.layoutMode == LayoutMode.FORCE_DIRECTED) {
				this.graph.addSink(this.forceDirectedLayout);
				this.forceDirectedLayout.addAttributeSink(this.graph);
				this.viewer.enableAutoLayout(this.forceDirectedLayout);
			}

			boolean layoutModeChanged = this.layoutMode != this.lastAppliedLayoutMode;
			if (firstRender || layoutModeChanged) {
				this.view.getCamera().resetView();
				if (this.layoutMode == LayoutMode.FORCE_DIRECTED) {
					// SpringBox spawns every node clustered near the origin and spreads them out
					// over the next several frames on GraphStream's own render thread, asynchronously,
					// well after this method returns - the resetView() just above only fits that
					// initial cluster, which is exactly the "still need to zoom/reset manually"
					// symptom switching into this mode had. A second, later fit - once physics has
					// actually had a chance to spread out - is what actually shows the settled graph.
					scheduleDelayedCameraReset();
				}
			}
			this.lastAppliedLayoutMode = this.layoutMode;
		} finally {
			this.view.setVisible(true);
		}

		if (this.layoutMode == LayoutMode.LANE) {
			computeContentBounds(snapshot);
			Platform.runLater(this::updateScrollBarRanges);
		}
	}

	/** See the call site in {@link #applySnapshot} for why force-directed mode needs a second, delayed camera fit. */
	private void scheduleDelayedCameraReset() {
		Thread thread = new Thread(() -> {
			try {
				Thread.sleep(FORCE_DIRECTED_SETTLE_DELAY_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			SwingUtilities.invokeLater(() -> {
				if (this.view != null && this.layoutMode == LayoutMode.FORCE_DIRECTED) {
					this.view.getCamera().resetView();
				}
			});
		});
		thread.setDaemon(true);
		thread.start();
	}

	/** See {@code FeaturesView.computeContentBounds} - same reasoning (camera metrics aren't reliable synchronously after a mutation). */
	private void computeContentBounds(KnowledgeGraphLayout.Snapshot snapshot) {
		if (snapshot.nodes.isEmpty()) {
			this.contentMinX = this.contentMaxX = this.contentMinY = this.contentMaxY = 0;
			return;
		}
		double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (KnowledgeGraphLayout.Placement node : snapshot.nodes) {
			minX = Math.min(minX, node.x - NODE_WIDTH / 2.0);
			maxX = Math.max(maxX, node.x + NODE_WIDTH / 2.0);
			minY = Math.min(minY, node.y - NODE_HEIGHT / 2.0);
			maxY = Math.max(maxY, node.y + NODE_HEIGHT / 2.0);
		}
		this.contentMinX = minX;
		this.contentMaxX = maxX;
		this.contentMinY = minY;
		this.contentMaxY = maxY;
	}

	private void updateScrollBarRanges() {
		this.horizontalScrollBar.setMin(this.contentMinX);
		this.horizontalScrollBar.setMax(this.contentMaxX);
		this.verticalScrollBar.setMin(this.contentMinY);
		this.verticalScrollBar.setMax(this.contentMaxY);
		syncScrollBarsFromCamera();
	}

	private void syncScrollBarsFromCamera() {
		if (null == view) return;
		Camera camera = view.getCamera();
		Point3 center = camera.getViewCenter();
		double visibleWidth = (this.contentMaxX - this.contentMinX) * camera.getViewPercent();
		double visibleHeight = (this.contentMaxY - this.contentMinY) * camera.getViewPercent();

		this.syncingScrollBars = true;
		try {
			this.horizontalScrollBar.setVisibleAmount(visibleWidth);
			this.horizontalScrollBar.setValue(clamp(center.x, this.contentMinX, this.contentMaxX));
			this.verticalScrollBar.setVisibleAmount(visibleHeight);
			this.verticalScrollBar.setValue(clamp(this.contentMinY + this.contentMaxY - center.y, this.contentMinY, this.contentMaxY));
		} finally {
			this.syncingScrollBars = false;
		}
	}

	private static double clamp(double value, double min, double max) {
		if (min > max) return min;
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * {@link CategoricalColorPalette}'s ordering only guarantees maximum perceptual distance between
	 * *adjacent* slots, not every pair - straight {@code kind.ordinal()} put COMMIT on aqua (slot 1)
	 * and VARIANT on green (slot 3), two hues close enough to read as the same color at node size.
	 * Hand-picked instead, spread across the wheel: blue / yellow / red / violet.
	 */
	private static int nodeColorIndex(KnowledgeGraphLayout.EntityKind kind) {
		switch (kind) {
			case FEATURE:
				return 0; // blue
			case ASSOCIATION:
				return 2; // yellow
			case COMMIT:
				return 5; // red
			case VARIANT:
				return 4; // violet
			default:
				return kind.ordinal();
		}
	}

	/** "#2a78d6" - GraphStream's inline "ui.style"/stylesheet attributes accept CSS hex colors directly. */
	private static String toHexColor(Color color) {
		return String.format("#%02x%02x%02x",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}


	/** Shared with {@link GraphCameraControls}'s Zoom In/Out buttons, so they clamp to the exact same range scroll/pinch-zoom already do. */
	private static final double MIN_VIEW_PERCENT = 0.05;
	private static final double MAX_VIEW_PERCENT = 1.0;
	private static final int NODE_WIDTH = 110;
	private static final int NODE_HEIGHT = 32;
	/** Matches CommitGraphView's node size - a physics-friendly scale, unlike LANE mode's much larger label-bearing boxes (see {@link #updateGraphStylesheet}). */
	private static final int FORCE_DIRECTED_NODE_SIZE = 24;
	/** "Size associations/commits by artifact count" ranges - deliberately not centered on the fixed node sizes above, so even the *largest* sized node in a snapshot reads as visually distinct from every other, unsized node kind. */
	private static final double SIZED_NODE_MIN_LANE = 24;
	private static final double SIZED_NODE_MAX_LANE = 160;
	private static final double SIZED_NODE_MIN_FORCE_DIRECTED = 8;
	private static final double SIZED_NODE_MAX_FORCE_DIRECTED = 60;
	private static final boolean DEFAULT_SHOW_LABELS = true;
	private static final int DEFAULT_COMMIT_LIMIT = 50;
	private static final int COMMIT_LIMIT_MAX = 1000;
	private static final long FORCE_DIRECTED_SETTLE_DELAY_MS = 900;
	private static final long HOVER_DELAY_MS = 200;
	private static final double HOVER_OVERLAY_OFFSET = 12;
	/** Matches every AssociationInfoArtifactViewer's own internal ListView#setFixedCellSize(20). */
	private static final double HOVER_PREVIEW_LINE_HEIGHT = 20;
	private static final double HOVER_PREVIEW_MIN_HEIGHT = 60;
	private static final double HOVER_PREVIEW_MAX_HEIGHT = 640;
	private static final double HOVER_PREVIEW_MIN_WIDTH = 200;
	private static final double HOVER_PREVIEW_MAX_WIDTH = 480;
	/** Extra room beyond raw measured text for the list's own insets/scrollbar. */
	private static final double HOVER_PREVIEW_WIDTH_PADDING = 40;
	/** Bounds the content scan on very large files - a preview only ever shows a handful of rows anyway. */
	private static final int HOVER_PREVIEW_SCAN_LIMIT = 500;
	private static final double COMMIT_PREVIEW_MIN_WIDTH = 160;
	private static final double COMMIT_PREVIEW_MAX_WIDTH = 360;
	private static final double COMMIT_PREVIEW_WIDTH_PADDING = 20;
	private static final DateFormat COMMIT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	/**
	 * Fixed colors per relation kind, reused from {@link CategoricalColorPalette}'s own hues for
	 * visual consistency rather than inventing a second palette - kept distinct from node fill
	 * color (which encodes entity-type lane instead, via {@link CategoricalColorPalette#colorForIndex}),
	 * so an edge's color and a node's fill never carry the same dimension of meaning.
	 */
	private static final Map<KnowledgeGraphLayout.EdgeKind, String> EDGE_COLORS = new EnumMap<>(KnowledgeGraphLayout.EdgeKind.class);

	static {
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.TOUCHES, "#4a3aa788");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.SELECTS, "#89878188");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.INVOLVES, "#eb683488");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.REQUIRES, "#1baf7a");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.EXCLUDES, "#e34948");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.PRODUCES_VARIANT, "#2a78d6");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.TOUCHES_COMPUTED, "#eda10099");
	}

	/**
	 * Called by the containing tab whenever it's selected or deselected - see
	 * {@code ArtifactGraphView#setTabVisible} for the full rationale (skip snapshot work while
	 * hidden, catch up with one fresh render when shown again).
	 */
	public void setTabVisible(boolean tabVisible) {
		boolean becameVisible = tabVisible && !this.tabVisible;
		this.tabVisible = tabVisible;
		if (becameVisible && this.service.isInitialized()) {
			Platform.runLater(() -> {
				if (this.viewer == null || this.view == null) {
					initView();
				}
				this.setDisable(false);
				this.refreshGraph();
			});
		}
	}

	/**
	 * See {@code ArtifactGraphView#statusChangedEvent} - the same rationale applies here: this is
	 * what actually fires after every commit, the snapshot is built synchronously on the calling
	 * thread for point-in-time correctness across a multi-commit burst, and it's skipped entirely
	 * while the tab is hidden.
	 */
	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			if (!this.tabVisible) {
				Platform.runLater(() -> this.setDisable(false));
				return;
			}
			KnowledgeGraphLayout.Snapshot snapshot = this.buildSnapshot();
			Platform.runLater(() -> {
				if (this.viewer == null || this.view == null) {
					initView();
				}
				this.setDisable(false);
				this.refreshGraph(snapshot);
			});
		} else {
			Platform.runLater(() -> {
				closeView();
				this.setDisable(true);
			});
		}
	}

}
