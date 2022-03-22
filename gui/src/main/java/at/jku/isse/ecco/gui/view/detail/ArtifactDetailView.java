package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.gui.view.ArtifactsView;
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
import java.util.stream.Collectors;

public class ArtifactDetailView extends BorderPane implements EccoListener {

	private final EccoService service;
	private final TabPane detailsTabPane;
	private final HashMap<String, Tab> openDetailsTabs = new HashMap<>();

	private boolean initialized;
	private Collection<AssociationInfo> associationInfos = null;
	private PartialOrderGraphView partialOrderGraphView;

	@Inject
	private Set<ArtifactViewer> artifactViewers;
	@Inject
	private Set<AssociationInfoArtifactViewer> associationInfoArtifactViewers;


	public ArtifactDetailView(EccoService service) {
		this.service = service;
		service.addListener(this);

		detailsTabPane = new TabPane();
		setCenter(detailsTabPane);
	}

	public void reset() {
		if (null != partialOrderGraphView) {
			partialOrderGraphView.closeGraph();
			partialOrderGraphView = null;
		}
		detailsTabPane.getTabs().clear();
		openDetailsTabs.clear();
	}

	public void showTree(Node node) {
		assert node != null;

		updateInfoTab(node);
		updatePartialOrderGraphTab(node);

		//long tm = System.nanoTime();
		updateArtifactViewerTabs(node);
		//System.out.println("\nArtifactDetailsView:showTree->updateArtifactViewerTabs: " + ((System.nanoTime() - tm)/1000000) + "ms");
	}

	private void updateInfoTab(Node node) {
		Tab t;
		if (openDetailsTabs.containsKey("info")) {
			t = openDetailsTabs.get("info");
		} else {
			t = new Tab("Info");
			t.setClosable(false);
			openDetailsTabs.put("info", t);
			detailsTabPane.getTabs().add(0, t);
		}

		// TODO: show some general info
		t.setContent(new Label("Detail View\n" + node.toString()));
	}

	private void updatePartialOrderGraphTab(Node node) {
		// if node is an ordered node display its sequence graph
		if (node.getArtifact() != null && node.getArtifact().getSequenceGraph() != null) {
			if (null == partialOrderGraphView) { partialOrderGraphView = new PartialOrderGraphView(); }

			Thread th = new Thread(() -> {
				PartialOrderGraph pog = node.getArtifact().getSequenceGraph();
				Platform.runLater(() -> partialOrderGraphView.showGraph(pog));
			});
			th.start();

			if (!openDetailsTabs.containsKey("pog")) {
				Tab t = new Tab("Graph");
				t.setClosable(false);
				t.setTooltip(new Tooltip("Partial order graph"));
				t.setContent(partialOrderGraphView);
				openDetailsTabs.put("pog", t);
				detailsTabPane.getTabs().add(1, t);
			}

		} else if (openDetailsTabs.containsKey("pog")) {
			detailsTabPane.getTabs().remove(openDetailsTabs.get("pog"));
			openDetailsTabs.remove("pog");
		}
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
				t = new Tab(v.getClass().getSimpleName());
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
			} else {
				return findNodeWithArtifactViewerRec(artifactDetailView, n);
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
			initialized = false;
			Platform.runLater(this::reset);
		}
	}

	public void setAssociationInfo(Collection<ArtifactsView.AssociationInfoImpl> associationInfos) {
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
