package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.gui.view.artifacts.AssociationInfoImpl;
import at.jku.isse.ecco.gui.view.graph.PartialOrderGraphView;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ArtifactDetailView extends BorderPane implements EccoListener {

	private final EccoService service;
	private final TabPane detailsTabPane;
	private final HashMap<String, Tab> openDetailsTabs = new HashMap<>();

	private boolean initialized;
	private Collection<AssociationInfo> associationInfos = null;
	private PartialOrderGraphView partialOrderGraphView;
	private final AtomicLong pogGeneration = new AtomicLong();
	/** The node whose order graph its tab shows once selected - drawn only then, see {@link #updatePartialOrderGraphTab}. */
	private Node pogNode;

	@Inject
	private Set<ArtifactViewer> artifactViewers;
	@Inject
	private Set<AssociationInfoArtifactViewer> associationInfoArtifactViewers;


	public ArtifactDetailView(EccoService service) {
		this.service = service;
		service.addListener(this);

		detailsTabPane = new TabPane();
		// what to do, while no node is shown (the pane was blank)
		Label placeholder = new Label("Select a node in the artifact tree to see its code, its order graph and details.");
		placeholder.setWrapText(true);
		placeholder.visibleProperty().bind(javafx.beans.binding.Bindings.isEmpty(detailsTabPane.getTabs()));
		setCenter(new javafx.scene.layout.StackPane(placeholder, detailsTabPane));

		// the order graph only while its tab is selected
		detailsTabPane.getSelectionModel().selectedItemProperty().addListener((o, was, tab) -> {
			Tab pogTab = openDetailsTabs.get("pog");
			if (pogTab == null)
				return;
			if (tab == pogTab)
				drawPartialOrderGraph();
			else if (was == pogTab && partialOrderGraphView != null)
				partialOrderGraphView.stopLayout();
		});
	}

	/**
	 * The key (e.g. "info"/"pog"/"AV_...") of whichever tab is currently selected, or null. Callers
	 * that are about to {@link #reset()} and rebuild (e.g. every recompose from a feature toggle in
	 * the live Artifacts panel) should capture this BEFORE resetting and pass it to
	 * {@link #showTree(Node, String)} afterwards - reset() throws away every Tab instance and
	 * rebuilds them fresh, so there's no way to recover "what was selected" from the rebuilt state
	 * itself; JavaFX also auto-selects the first tab added to an empty TabPane, which a
	 * selection-listener-based approach can't distinguish from a real user click, since that
	 * auto-selection fires mid-rebuild, before a listener-based capture could be read back.
	 */
	public String getSelectedTabKey() {
		Tab selected = detailsTabPane.getSelectionModel().getSelectedItem();
		for (Map.Entry<String, Tab> entry : openDetailsTabs.entrySet()) {
			if (entry.getValue() == selected) {
				return entry.getKey();
			}
		}
		return null;
	}

	public void reset() {
		pogNode = null;
		if (null != partialOrderGraphView) {
			partialOrderGraphView.closeGraph();
			partialOrderGraphView = null;
		}
		detailsTabPane.getTabs().clear();
		openDetailsTabs.clear();
	}

	public void showTree(Node node) {
		showTree(node, null);
	}

	/**
	 * @param restoreTabKey a key previously returned by {@link #getSelectedTabKey()} (captured
	 *                      BEFORE calling {@link #reset()}), to restore afterwards if a tab of the
	 *                      same kind still exists - otherwise every recompose (e.g. a feature
	 *                      toggle in the live Artifacts panel) would silently jump back to the Info
	 *                      tab even if the user was looking at, say, a lilypond code viewer. Pass
	 *                      null to just let the rebuild pick its own default (e.g. a first-ever
	 *                      showTree() call, with nothing to restore).
	 */
	public void showTree(Node node, String restoreTabKey) {
		if (node == null) {
			// e.g. no node in this tree has a registered ArtifactViewer (a plain commit with no
			// adapter-specific viewer, such as pure Java/text content)
			this.reset();
			return;
		}

		Tab selected = detailsTabPane.getSelectionModel().getSelectedItem();
		updateInfoTab(node);
		updatePartialOrderGraphTab(node);
		updateArtifactViewerTabs(node);
		orderTabs();

		// the tab open before stays open (also across a recompose); else the first: the code
		if (restoreTabKey != null && openDetailsTabs.containsKey(restoreTabKey))
			detailsTabPane.getSelectionModel().select(openDetailsTabs.get(restoreTabKey));
		else if (selected != null && detailsTabPane.getTabs().contains(selected))
			detailsTabPane.getSelectionModel().select(selected);
		else
			detailsTabPane.getSelectionModel().selectFirst();
		// its tab stayed selected: no selection change draws the new node's graph
		if (openDetailsTabs.containsKey("pog") && detailsTabPane.getSelectionModel().getSelectedItem() == openDetailsTabs.get("pog"))
			drawPartialOrderGraph();
	}

	/**
	 * The viewers' tabs first (the code, as the adapters show it), then the order graph, then the
	 * node's details - it opened on the details, which said little, with the code in the last tab.
	 */
	private void orderTabs() {
		List<Tab> ordered = new ArrayList<>();
		openDetailsTabs.entrySet().stream().filter(e -> e.getKey().startsWith("AV_"))
				.sorted(Comparator.comparingInt(e -> detailsTabPane.getTabs().indexOf(e.getValue())))
				.forEach(e -> ordered.add(e.getValue()));
		if (openDetailsTabs.containsKey("pog"))
			ordered.add(openDetailsTabs.get("pog"));
		if (openDetailsTabs.containsKey("info"))
			ordered.add(openDetailsTabs.get("info"));
		if (!ordered.equals(detailsTabPane.getTabs()))
			detailsTabPane.getTabs().setAll(ordered);
	}

	private void updateInfoTab(Node node) {
		Tab t;
		if (openDetailsTabs.containsKey("info")) {
			t = openDetailsTabs.get("info");
		} else {
			t = new Tab("Info");
			t.setClosable(false);
			openDetailsTabs.put("info", t);
			detailsTabPane.getTabs().add(t);
		}

		Label info = new Label(describe(node));
		info.setWrapText(true);
		info.setPadding(new javafx.geometry.Insets(8));
		t.setContent(info);
	}

	/** What the Info tab says about a node: what it is, where, its association and how its children are kept. */
	private String describe(Node node) {
		StringBuilder text = new StringBuilder(node.toString());
		List<String> path = new ArrayList<>();
		for (Node parent = node.getParent(); parent != null; parent = parent.getParent())
			if (parent.getArtifact() != null && !String.valueOf(parent.getArtifact()).isBlank())
				path.add(0, String.valueOf(parent.getArtifact()));
		if (!path.isEmpty())
			text.append("\n\nIn: ").append(String.join(" > ", path));
		if (node.getArtifact() != null) {
			at.jku.isse.ecco.core.Association association = node.getArtifact().getContainingNode() == null ? null
					: node.getArtifact().getContainingNode().getContainingAssociation();
			if (association != null) {
				text.append("\n\nAssociation: ").append(at.jku.isse.ecco.gui.TableColumns.shortIds(association.getId()));
				String condition = this.conditionOf(association);
				if (condition != null)
					text.append("\nCondition: ").append(condition);
			}
			text.append("\n\nOrdered children: ").append(node.getArtifact().isOrdered() ? "yes" : "no");
			if (node.getArtifact().getSequenceNumber() >= 0)
				text.append("\nPosition in its parent's order: ").append(node.getArtifact().getSequenceNumber());
		}
		text.append("\nUnique to its association: ").append(node.isUnique() ? "yes" : "no")
				.append("\nAtomic (compared as a whole): ").append(node.isAtomic() ? "yes" : "no");
		return text.toString();
	}

	/** The association's minimized condition, as the associations table shows it, else its full condition. */
	private String conditionOf(at.jku.isse.ecco.core.Association association) {
		if (this.associationInfos != null)
			for (AssociationInfo info : this.associationInfos)
				if (info.getAssociation() == association && info instanceof AssociationInfoImpl impl
						&& impl.getMinimizedCondition() != null && !impl.getMinimizedCondition().isEmpty())
					return impl.getMinimizedCondition();
		if (this.service.isWriteInProgress())
			return null;
		return association.computeCondition().toDisplayString();
	}

	private void updatePartialOrderGraphTab(Node node) {
		// if node is an ordered node display its sequence graph
		if (node.getArtifact() != null && node.getArtifact().getPartialOrderGraph() != null) {
			if (null == partialOrderGraphView) {
				partialOrderGraphView = new PartialOrderGraphView();
				partialOrderGraphView.setOnBlockChosen(this::showInCode);
			}
			// drawn when its tab is selected: drawn for every node shown, the graph of a large file -
			// a node per line - was laid out and redrawn without end in the hidden tab, until the heap ran out
			pogNode = node;
			pogGeneration.incrementAndGet();

			if (!openDetailsTabs.containsKey("pog")) {
				Tab t = new Tab("Order graph");
				t.setClosable(false);
				t.setTooltip(new Tooltip("Partial order graph"));
				t.setContent(partialOrderGraphView);
				openDetailsTabs.put("pog", t);
				detailsTabPane.getTabs().add(t);
			}

		} else if (openDetailsTabs.containsKey("pog")) {
			pogNode = null;
			if (partialOrderGraphView != null)
				partialOrderGraphView.stopLayout();
			detailsTabPane.getTabs().remove(openDetailsTabs.get("pog"));
			openDetailsTabs.remove("pog");
		}
	}

	/**
	 * Shows [child] - a block chosen in the order graph - in the first code tab: at its line, the tab
	 * selected. The tree's selection stays: the order graph's tab with it, for the next block.
	 */
	private void showInCode(Node child) {
		for (Tab tab : detailsTabPane.getTabs()) {
			if (tab.getContent() instanceof ArtifactViewer viewer && openDetailsTabs.entrySet().stream().anyMatch(e -> e.getValue() == tab && e.getKey().startsWith("AV_"))) {
				viewer.showTree(child);
				detailsTabPane.getSelectionModel().select(tab);
				return;
			}
		}
	}

	/** Draws the order graph of {@link #pogNode}, if not drawn yet - only while its tab is selected. */
	private void drawPartialOrderGraph() {
		Node node = pogNode;
		if (node == null || partialOrderGraphView == null)
			return;
		pogNode = null; // drawn: selecting the tab again keeps it
		long generation = pogGeneration.incrementAndGet();
		PartialOrderGraphView graphView = partialOrderGraphView;
		Thread th = new Thread(() -> {
			PartialOrderGraph pog = node.getArtifact().getPartialOrderGraph();
			Platform.runLater(() -> {
				if (pogGeneration.get() != generation || graphView != partialOrderGraphView) { return; }
				graphView.showGraph(pog, node, this.service.isInitialized() ? this.service.getRepository() : null, this.service);
			});
		});
		th.setDaemon(true);
		th.start();
	}

	private void updateArtifactViewerTabs(Node node) {
		List<? extends ArtifactViewer> viewers = getArtifactViewers(node);
		List<String> tabKeysToRemove = openDetailsTabs.keySet().stream()
				.filter(k -> k.startsWith("AV_"))
				.collect(Collectors.toList());

		for (ArtifactViewer v : viewers) {
			String key = "AV_" + v.getClass().toString();
			tabKeysToRemove.remove(key);

			Tab t;
			if (openDetailsTabs.containsKey(key)) {
				t = openDetailsTabs.get(key);
			} else {
				t = new Tab(v.getTabName());
				t.setTooltip(new Tooltip(v.getClass().getSimpleName()));
				t.setClosable(false);
				openDetailsTabs.put(key, t);
				detailsTabPane.getTabs().add(t);
			}

			try {
				if (v instanceof AssociationInfoArtifactViewer) {
					((AssociationInfoArtifactViewer)v).setAssociationInfos(associationInfos);
				}
				v.showTree(node);
				t.setContent((Pane) v);

			} catch (Exception ex) {
				TextArea exceptionTextArea = new TextArea();
				exceptionTextArea.setEditable(false);

				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ex.printStackTrace(pw);
				String exceptionText = sw.toString();

				exceptionTextArea.setText(exceptionText);
				t.setContent(exceptionTextArea);
			}
		}

		for (String key : tabKeysToRemove) {
			detailsTabPane.getTabs().remove(openDetailsTabs.get(key));
			openDetailsTabs.remove(key);
		}
	}

	/**
	 * Checks for injected {@link ArtifactViewer}s or {@link AssociationInfoArtifactViewer}s with matching plugin id
	 * and adds them to resulting list if the viewer is an instance of {@link Pane}.
	 * @param node The node to find {@link ArtifactViewer}s for.
	 * @return A list of matching {@link ArtifactViewer}s or an empty list if there are no matches,
	 * or the service is not initialized.
	 */
	private List<ArtifactViewer> getArtifactViewers(Node node) {
		LinkedList<ArtifactViewer> viewers = new LinkedList<>();
		if (service.isInitialized()) {
			if (!initialized) {
				service.getInjector().injectMembers(this);
				initialized = true;
			}
		} else {
			initialized = false;
			return viewers;
		}

		String pluginId = getPluginId(node);
		if (artifactViewers != null) {
			for (ArtifactViewer tempArtifactViewer : artifactViewers) {
				if (tempArtifactViewer.getPluginId() != null && tempArtifactViewer instanceof Pane) {
					if (tempArtifactViewer.getPluginId().equals(pluginId)) {
						viewers.add(tempArtifactViewer);
					}
				}
			}
		}

		if (associationInfoArtifactViewers != null) {
			for (AssociationInfoArtifactViewer tempAssInfoArtifactViewer : associationInfoArtifactViewers) {
				if (tempAssInfoArtifactViewer.getPluginId() != null && tempAssInfoArtifactViewer instanceof Pane) {
					if (tempAssInfoArtifactViewer.getPluginId().equals(pluginId)) {
						viewers.add(tempAssInfoArtifactViewer);
					}
				}
			}
		}

		return viewers;
	}

	public static Node findNodeWithArtifactViewerRec(ArtifactDetailView artifactDetailView, Node node) {
		for (Node n : node.getChildren()) {
			if (artifactDetailView.getArtifactViewers(n).size() > 0) {
				return n;
			}
			Node found = findNodeWithArtifactViewerRec(artifactDetailView, n);
			if (found != null) {
				return found;
			}
		}
		return null;
	}


	/**
	 * Retrieves the ID of the plugin that created the given node. If the node was created by an artifact plugin the plugin's ID is returned. If the node was not creaetd by a plugin, as is for example the case with directories, null is returned.
	 *
	 * @param node The node for which the plugin ID shall be retrieved.
	 * @return The plugin ID of the given node or null if the node was not created by a plugin.
	 */
	public static String getPluginId(Node node) {
		if (node == null || node.getArtifact() == null)
			return null;
		else {
			if (node.getArtifact().getData() instanceof PluginArtifactData)
				return ((PluginArtifactData) node.getArtifact().getData()).getPluginId();
			else {
				return getPluginId(node.getParent());
			}
		}
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (!service.isInitialized()) {
			// initialized is read and written by getArtifactViewers() on the FX thread
			Platform.runLater(() -> {
				initialized = false;
				this.reset();
			});
		}
	}

	public void setAssociationInfo(Collection<AssociationInfoImpl> associationInfos) {
		this.associationInfos = associationInfos == null ? null :
				associationInfos.stream()
				.map(aii -> (AssociationInfo)aii)
				.collect(Collectors.toCollection(ArrayList::new));

		if (associationInfoArtifactViewers != null) {
			for (AssociationInfoArtifactViewer v : associationInfoArtifactViewers) {
				v.setAssociationInfos(this.associationInfos);
			}
		}
	}
}
