package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.RepositoryAccess;
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
	/** The node whose order graph its tab shows once selected - drawn only then, see {@link #updatePartialOrderGraphTab}. */
	private Node pogNode;
	/** The order graph of {@link #pogNode}, got holding the service's lock. */
	private PartialOrderGraph pog;

	/**
	 * Counts the requests to show a node: a node is read in the background, holding the service's
	 * lock (see {@link #show}), and only the latest request's result is shown. FX thread only.
	 */
	private long requests;
	private boolean reading;
	/** The copy of the tree the viewers show (see {@link #viewerCopy}): a block chosen in the order graph is looked up in it. */
	private NodeSnapshot shownCopy;
	/** Counts the repository's changes ({@link #statusChangedEvent}): a copy made before one is made again. */
	private final AtomicLong repositoryChanges = new AtomicLong();
	/** The last copy made for the viewers, read and replaced holding the service's lock. */
	private volatile CachedCopy cachedCopy;

	private record CachedCopy(Node root, String pluginId, long changes, NodeSnapshot snapshot) {
	}

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
		requests++; // a node still being read is not shown
		reading = false;
		pogNode = null;
		pog = null;
		shownCopy = null;
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
	 * Shows [node] - once it is read, in the background, holding the service's lock (see {@link #show}).
	 *
	 * @param restoreTabKey a key previously returned by {@link #getSelectedTabKey()} (captured
	 *                      BEFORE calling {@link #reset()}), to restore afterwards if a tab of the
	 *                      same kind still exists - otherwise every recompose (e.g. a feature
	 *                      toggle in the live Artifacts panel) would silently jump back to the Info
	 *                      tab even if the user was looking at, say, a lilypond code viewer. Pass
	 *                      null to just let the rebuild pick its own default (e.g. a first-ever
	 *                      showTree() call, with nothing to restore).
	 */
	public void showTree(Node node, String restoreTabKey) {
		this.show(node, false, restoreTabKey);
	}

	/**
	 * Shows the first node below [root] a viewer can show - its code, without a click on a node first
	 * - else [root] itself (its Info tab); found in the background, holding the service's lock. See
	 * {@link #showTree(Node, String)} for [restoreTabKey].
	 */
	public void showFirstViewable(Node root, String restoreTabKey) {
		this.show(root, true, restoreTabKey);
	}

	/** Whether a node is still being read - for tests. */
	public boolean isReading() {
		return this.reading;
	}

	/**
	 * Reads what the tabs show of [node] (or of the first node below it a viewer can show) - its
	 * description, condition, order graph and a copy of its tree for the viewers - in the background,
	 * holding the service's lock: they were read on the FX thread while a commit changed them (the
	 * check whether one ran, before the condition was computed, did not keep one from starting right
	 * after it), and the viewers walked the tree there. Until then the tabs stay, the Info tab saying
	 * the node is being read; a request made meanwhile supersedes this one.
	 */
	private void show(Node node, boolean firstViewable, String restoreTabKey) {
		if (node == null || !this.service.isInitialized()) {
			// e.g. no node in this tree has a registered ArtifactViewer (a plain commit with no
			// adapter-specific viewer, such as pure Java/text content); nothing of a closed repository
			// is shown either - closing it cleared the tabs
			if (!this.service.isInitialized())
				this.initialized = false;
			this.reset();
			return;
		}
		long request = ++this.requests;
		Set<String> viewable = this.viewerPluginIds();
		this.reading = true;
		Tab infoTab = this.openDetailsTabs.get("info");
		if (infoTab != null)
			infoTab.setContent(infoLabel("Reading the node..."));
		RepositoryAccess.read(this.service, "Artifact details", svc -> this.read(node, firstViewable, viewable),
				details -> {
					if (request != this.requests)
						return;
					this.reading = false;
					this.apply(details, restoreTabKey);
				},
				failure -> {
					if (request != this.requests)
						return;
					this.reading = false;
					new ExceptionAlert(failure).show();
				});
	}

	/** What the tabs show of a node (the original, not a copy), read holding the service's lock. */
	private record Details(Node node, String described, Association association, String condition, String flags,
						   PartialOrderGraph pog, String pluginId, NodeSnapshot copy, Node viewed) {
	}

	/** Reads [node] (or the first node below it of a plugin in [viewable]) - holding the service's lock. */
	private Details read(Node node, boolean firstViewable, Set<String> viewable) {
		Node live = node.getNode();
		if (firstViewable) {
			Node found = firstWithViewer(live, viewable);
			if (found != null)
				live = found;
		}
		StringBuilder text = new StringBuilder(live.toString());
		List<String> path = new ArrayList<>();
		for (Node parent = live.getParent(); parent != null; parent = parent.getParent())
			if (parent.getArtifact() != null && !String.valueOf(parent.getArtifact()).isBlank())
				path.add(0, String.valueOf(parent.getArtifact()));
		if (!path.isEmpty())
			text.append("\n\nIn: ").append(String.join(" > ", path));
		Association association = null;
		String condition = null;
		StringBuilder flags = new StringBuilder();
		if (live.getArtifact() != null) {
			association = live.getArtifact().getContainingNode() == null ? null
					: live.getArtifact().getContainingNode().getContainingAssociation();
			if (association != null)
				condition = association.computeCondition().toDisplayString();
			flags.append("\n\nOrdered children: ").append(live.getArtifact().isOrdered() ? "yes" : "no");
			if (live.getArtifact().getSequenceNumber() >= 0)
				flags.append("\nPosition in its parent's order: ").append(live.getArtifact().getSequenceNumber());
		}
		flags.append("\nUnique to its association: ").append(live.isUnique() ? "yes" : "no")
				.append("\nAtomic (compared as a whole): ").append(live.isAtomic() ? "yes" : "no");
		PartialOrderGraph graph = live.getArtifact() != null ? live.getArtifact().getPartialOrderGraph() : null;
		String pluginId = getPluginId(live);
		NodeSnapshot copy = pluginId != null && viewable.contains(pluginId) ? this.viewerCopy(live, pluginId) : null;
		return new Details(live, text.toString(), association, condition, flags.toString(), graph, pluginId, copy, copy == null ? null : copy.copyOf(live));
	}

	/**
	 * A copy of the tree [node] is in, for the viewers of plugin [pluginId]: they walk it from its
	 * root, each file of theirs in a tab - the files of other plugins are copied without their
	 * content. Made once per tree and plugin, until the repository changes; holding the service's lock.
	 */
	private NodeSnapshot viewerCopy(Node node, String pluginId) {
		Node root = node;
		while (root.getParent() != null)
			root = root.getParent();
		long changes = this.repositoryChanges.get();
		CachedCopy cached = this.cachedCopy;
		if (cached != null && cached.root() == root && cached.pluginId().equals(pluginId) && cached.changes() == changes && cached.snapshot().copyOf(node) != null)
			return cached.snapshot();
		NodeSnapshot copy = NodeSnapshot.of(root, n -> !(n.getArtifact() != null && n.getArtifact().getData() instanceof PluginArtifactData data
				&& !pluginId.equals(data.getPluginId())));
		this.cachedCopy = new CachedCopy(root, pluginId, changes, copy);
		return copy;
	}

	/** Shows [details], read for the latest request. */
	private void apply(Details details, String restoreTabKey) {
		Tab selected = detailsTabPane.getSelectionModel().getSelectedItem();
		updateInfoTab(details);
		updatePartialOrderGraphTab(details);
		updateArtifactViewerTabs(details);
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

	private void updateInfoTab(Details details) {
		Tab t;
		if (openDetailsTabs.containsKey("info")) {
			t = openDetailsTabs.get("info");
		} else {
			t = new Tab("Info");
			t.setClosable(false);
			openDetailsTabs.put("info", t);
			detailsTabPane.getTabs().add(t);
		}

		t.setContent(infoLabel(describe(details)));
	}

	private static Label infoLabel(String text) {
		Label info = new Label(text);
		info.setWrapText(true);
		info.setPadding(new javafx.geometry.Insets(8));
		return info;
	}

	/** What the Info tab says about a node: what it is, where, its association and how its children are kept. */
	private String describe(Details details) {
		StringBuilder text = new StringBuilder(details.described());
		if (details.association() != null) {
			text.append("\n\nAssociation: ").append(at.jku.isse.ecco.gui.TableColumns.shortIds(details.association().getId()));
			String condition = this.minimizedConditionOf(details.association());
			if (condition == null)
				condition = details.condition();
			if (condition != null)
				text.append("\nCondition: ").append(condition);
		}
		return text.append(details.flags()).toString();
	}

	/** The association's minimized condition, as the associations table shows it, else null (then its full condition is shown). */
	private String minimizedConditionOf(Association association) {
		if (this.associationInfos != null)
			for (AssociationInfo info : this.associationInfos)
				if (info.getAssociation() == association && info instanceof AssociationInfoImpl impl
						&& impl.getMinimizedCondition() != null && !impl.getMinimizedCondition().isEmpty())
					return impl.getMinimizedCondition();
		return null;
	}

	private void updatePartialOrderGraphTab(Details details) {
		// if node is an ordered node display its sequence graph
		if (details.pog() != null) {
			if (null == partialOrderGraphView) {
				partialOrderGraphView = new PartialOrderGraphView();
				partialOrderGraphView.setOnBlockChosen(this::showInCode);
			}
			// drawn when its tab is selected: drawn for every node shown, the graph of a large file -
			// a node per line - was laid out and redrawn without end in the hidden tab, until the heap ran out
			pogNode = details.node();
			pog = details.pog();

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
			pog = null;
			detailsTabPane.getTabs().remove(openDetailsTabs.get("pog"));
			openDetailsTabs.remove("pog");
		}
	}

	/**
	 * Shows [child] - a block chosen in the order graph - in the first code tab: at its line, the tab
	 * selected. The tree's selection stays: the order graph's tab with it, for the next block.
	 */
	private void showInCode(Node child) {
		// the viewers show a copy of the tree: the chosen node's copy
		Node copy = shownCopy == null ? null : shownCopy.copyOf(child);
		if (copy == null)
			return;
		for (Tab tab : detailsTabPane.getTabs()) {
			if (tab.getContent() instanceof ArtifactViewer viewer && openDetailsTabs.entrySet().stream().anyMatch(e -> e.getValue() == tab && e.getKey().startsWith("AV_"))) {
				viewer.showTree(copy);
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
		// the graph was got holding the service's lock (a thread of its own got it without), and is
		// read holding it too
		partialOrderGraphView.showGraph(pog, node, () -> this.service.isInitialized() ? this.service.getRepository() : null, this.service);
	}

	private void updateArtifactViewerTabs(Details details) {
		// a copy of the tree: the viewers walk it on the FX thread
		List<? extends ArtifactViewer> viewers = details.viewed() == null ? List.of() : viewersFor(details.pluginId());
		shownCopy = details.copy();
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
				v.showTree(details.viewed());
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
	 * @param pluginId The plugin to find {@link ArtifactViewer}s for (may be null: none).
	 * @return A list of matching {@link ArtifactViewer}s or an empty list if there are no matches,
	 * or the service is not initialized.
	 */
	private List<ArtifactViewer> viewersFor(String pluginId) {
		LinkedList<ArtifactViewer> viewers = new LinkedList<>();
		if (!injected() || pluginId == null)
			return viewers;

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

	/** The plugins a viewer is registered for - none while the repository is not open. */
	private Set<String> viewerPluginIds() {
		Set<String> pluginIds = new HashSet<>();
		if (!injected())
			return pluginIds;
		for (Set<? extends ArtifactViewer> registered : Arrays.<Set<? extends ArtifactViewer>>asList(artifactViewers, associationInfoArtifactViewers))
			if (registered != null)
				for (ArtifactViewer viewer : registered)
					if (viewer.getPluginId() != null && viewer instanceof Pane)
						pluginIds.add(viewer.getPluginId());
		return pluginIds;
	}

	/** Injects the viewers once the repository is open; false while it is not. */
	private boolean injected() {
		if (service.isInitialized()) {
			if (!initialized) {
				service.getInjector().injectMembers(this);
				initialized = true;
			}
			return true;
		}
		initialized = false;
		return false;
	}

	/**
	 * The first node below [node] a viewer of [artifactDetailView] can show, or null. Walks the tree:
	 * call it holding the service's lock, off the FX thread - or see {@link #showFirstViewable}.
	 */
	public static Node findNodeWithArtifactViewerRec(ArtifactDetailView artifactDetailView, Node node) {
		return firstWithViewer(node, artifactDetailView.viewerPluginIds());
	}

	private static Node firstWithViewer(Node node, Set<String> pluginIds) {
		for (Node n : node.getChildren()) {
			if (pluginIds.contains(getPluginId(n))) {
				return n;
			}
			Node found = firstWithViewer(n, pluginIds);
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
		// the repository changed: a copy of a tree made before is not shown again
		this.repositoryChanges.incrementAndGet();
		if (!service.isInitialized()) {
			this.cachedCopy = null;
			// initialized is read and written by injected() on the FX thread
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
