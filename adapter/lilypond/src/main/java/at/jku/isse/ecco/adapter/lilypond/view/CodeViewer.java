package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.data.context.BaseContextArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.beans.PropertyChangeListener;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

public class CodeViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private final HashMap<String, AssociationInfo> associationInfos;
	private final HashMap<String, PropertyChangeListener> associationListeners;
	private final ListView<NodeTextBlock[]> listView;
	private final TextArea taInfo;
	private final HashMap<String, int[]> nodeIdIndexes = new HashMap<>();
	private final ObservableList<NodeTextBlock[]> codeLines = FXCollections.observableArrayList();
	private Path currentFile;
	private Node root;
	private volatile boolean isTreeInitialized = false;

	public CodeViewer() {
		associationInfos = new HashMap<>();
		associationListeners = new HashMap<>();
		listView = new ListView<>(codeLines);
		listView.setSelectionModel(new NoSelectionModel<>());
		listView.setFocusTraversable(false);

		listView.setCellFactory(new Callback<>() {
			@Override
			public ListCell<NodeTextBlock[]> call(ListView<NodeTextBlock[]> param) {
				ListCell<NodeTextBlock[]> cell = new ListCell<>() {
					@Override
					protected void updateItem(NodeTextBlock[] blocks, boolean empty) {
						HBox old = (HBox)getGraphic();
						if (old != null) {
							for (javafx.scene.Node n : old.getChildren()) {
								if (n instanceof TextBlockLabel tbl) {
									tbl.highlightedProperty().unbind();
								}
							}
						}

						super.updateItem(blocks, empty);

						if (empty || null == blocks) {
							setGraphic(null);
						} else {
							HBox box = getCellContent(blocks);
							setGraphic(box);
						}
					}
				};
				URL url = ClassLoader.getSystemResource("styles/CodeViewer.css");
				if (url != null) {
					cell.getStylesheets().add(url.toExternalForm());
				}
				return cell;
			}
		});

		taInfo = new TextArea();
		taInfo.setMinHeight(60);
		taInfo.setWrapText(true);

		SplitPane.setResizableWithParent(taInfo, false);
		SplitPane sp = new SplitPane(listView, taInfo);
		sp.setOrientation(Orientation.VERTICAL);

		this.setCenter(sp);
		//BorderPane.setAlignment(sp, Pos.TOP_LEFT);

		ArtifactDataLabelNode.setInfoArea(taInfo);

		Platform.runLater(() -> sp.setDividerPositions(0.95));
	}

	private HBox getCellContent(NodeTextBlock[] blocks) {
		HBox box = new HBox();
		for (NodeTextBlock ntb : blocks) {
			TextBlockLabel l = new TextBlockLabel(ntb);
			if (!ntb.isFirst() && !ntb.isLast()) {
				l.getStyleClass().add("innerBlock");
			} else if (!ntb.isLast()) {
				l.getStyleClass().add("firstBlock");
			} else if (!ntb.isFirst()) {
				l.getStyleClass().add("lastBlock");
			}

			l.setOnMouseEntered(e -> {
				l.setBackground(new Background(new BackgroundFill(Color.rgb(50, 197, 255), null, null)));
				showAssociationInfo(ntb.getAssociation());
			});
			l.setOnMouseExited(e -> l.setBackground(new Background(new BackgroundFill(
					l.getTextBlock().backgroundColorProperty().get(), null, null))));

			l.highlightedProperty().set(ntb.highlightedProperty().getValue());
			l.highlightedProperty().bind(ntb.highlightedProperty());

			box.getChildren().add(l);
		}
		return box;
	}

	@Override
	public void showTree(Node node) {
		final Node n = node.getNode(); // in case of a wrapped node
		root = getPluginNode(n);
		if (root == null) { return; }

		PluginArtifactData pad = (PluginArtifactData)root.getArtifact().getData();
		if (!pad.getFileName().equals(currentFile)) {
			isTreeInitialized = false;
			currentFile = pad.getFileName();
			nodeIdIndexes.clear();
			nodeIdIndexes.put("0", new int[]{0, 0});

			Task<Void> buildTask = new Task<>() {
				@Override
				protected Void call() {
					final ArrayList<NodeTextBlock[]> lines = new ArrayList<>();
					ArrayList<NodeTextBlock> line = new ArrayList<>();
					int[] pos = new int[]{0,0};
					int idx = 0;
					for (Node cn : root.getChildren()) {
						String id = "0.".concat(String.valueOf(idx));
						nodeIdIndexes.put(id, new int[]{pos[0], pos[1]});
						line = buildCodeLinesRec(cn, id, pos, lines, line);
						idx++;
					}
					NodeTextBlock[] lastLine = new NodeTextBlock[line.size()];
					lines.add(line.toArray(lastLine));

					isTreeInitialized = true;
					Platform.runLater(() -> {
						codeLines.clear();
						codeLines.addAll(lines);
						highlightTree(n);
					});
					return null;
				}
			};
			new Thread(buildTask).start();

		} else if (isTreeInitialized) {
			highlightTree(n);
		}
	}

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}

	private Node getPluginNode(Node n) {
		while (n != null && !(n.getArtifact().getData() instanceof PluginArtifactData)) {
			n = n.getParent();
		}
		return n;
	}

	private void highlightTree(Node node) {
		String curId = calculateNodeId(node);
		int[] pos = nodeIdIndexes.get(curId);
		listView.scrollTo(pos[0]);

		NodeTextBlock ntb;
		do {
			NodeTextBlock[] line = codeLines.get(pos[0]);
			ntb = line[pos[1]];
			ntb.setHighlighted(true);
			if (pos[1] < line.length - 1) {
				pos[1]++;
			} else {
				pos[0]++;
				pos[1] = 0;
			}

		} while (!ntb.isLast());
	}

	private String calculateNodeId(Node node) {
		StringBuilder sb = new StringBuilder();
		while (node != null && node != root) {
			List<? extends Node> children = node.getParent().getChildren();
			int i = 0;
			while (children.get(i) != node.getNode()) { // compare by reference
				i++;
			}
			sb.insert(0, i)
					.insert(0, ".");
			node = node.getParent();
		}
		sb.insert(0, "0");
		return sb.toString();
	}

	private ArrayList<NodeTextBlock> buildCodeLinesRec(Node n, String nodeId, int[] pos, Collection<NodeTextBlock[]> lines, ArrayList<NodeTextBlock> line) {
		ArtifactData d = n.getArtifact().getData();
		if (d instanceof BaseContextArtifactData) {
			List<? extends Node> children = n.getChildren();
			for (int i = 0; i < children.size(); i++) {
				String id = nodeId.concat(".").concat(String.valueOf(i));
				nodeIdIndexes.put(id, new int[]{pos[0], pos[1]});
				line = buildCodeLinesRec(children.get(i), id, pos, lines, line);
			}

		} else if (d instanceof DefaultTokenArtifactData) {
			Association ass = n.getArtifact().getContainingNode() != null
					? n.getArtifact().getContainingNode().getContainingAssociation()
					: null;
			Color bgCol = Color.WHITE;
			if (ass != null) {
				String aiId = ass.getId();
				if (associationInfos.containsKey(aiId)) {
					Object val = associationInfos.get(aiId).getPropertyValue("color");
					if (val instanceof Color col && !col.equals(Color.TRANSPARENT)) {
						bgCol = col;
					}
				}
			}

			NodeTextBlock ntb = new NodeTextBlock(n, bgCol);

			line.add(ntb);
			pos[1]++;

			if (ntb.numLines() > 1) {
				NodeTextBlock[] l = new NodeTextBlock[line.size()];
				lines.add(line.toArray(l));
				pos[0]++;

				for (int i = 1; i < ntb.numLines(); i++) {
					if (i < ntb.numLines() - 1) {
						lines.add(new NodeTextBlock[]{ntb.getGroup().get(i)});
						pos[0]++;
					} else {
						line = new ArrayList<>();
						line.add(ntb.getGroup().get(i));
						pos[1] = 1;
					}
				}
			}
		}

		return line;
	}

	private void showAssociationInfo(Association a) {
		if (taInfo == null || a == null) return;

		Condition c = a.computeCondition();
		taInfo.setText(a.getId().concat(" (").concat(c.getSimpleModuleRevisionConditionString()).concat(")\n").concat(c.getModuleRevisionConditionString()));
	}

	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
		currentFile = null; // rebuild tree on next 'showTree()'

		// remove listeners
		for (Map.Entry<String, AssociationInfo> entry : this.associationInfos.entrySet()) {
			entry.getValue().removePropertyChangeListener(associationListeners.get(entry.getKey()));
		}

		this.associationInfos.clear();
		associationListeners.clear();
		if (associationInfos == null) {
			return;
		}

		for (AssociationInfo ai : associationInfos) {
			this.associationInfos.put(ai.getAssociation().getId(), ai);
		}

		// add listeners
		for (AssociationInfo ai : this.associationInfos.values()) {
			final PropertyChangeListener pcl = getColorPropertyListener();
			ai.addPropertyChangeListener(pcl);
			associationListeners.put(ai.getAssociation().getId(), pcl);
		}
		listView.refresh();
	}

	private PropertyChangeListener getColorPropertyListener() {
		return evt -> {
			if (evt.getPropertyName().equals("color")) {
				String aId = ((AssociationInfo)evt.getSource()).getAssociation().getId();
				for (NodeTextBlock[] blocks : codeLines) {
					for (NodeTextBlock ntb : blocks) {
						if (ntb.getAssociation() != null && aId.equals(ntb.getAssociation().getId())) {
							ntb.setBackgroundColor((Color)evt.getNewValue());
						}
					}
				}
				listView.refresh();
			}
		};
	}

	private static class NoSelectionModel<T> extends MultipleSelectionModel<T> {

		@Override
		public ObservableList<Integer> getSelectedIndices() {
			return FXCollections.emptyObservableList();
		}

		@Override
		public ObservableList<T> getSelectedItems() {
			return FXCollections.emptyObservableList();
		}

		@Override
		public void selectIndices(int index, int... indices) {
		}

		@Override
		public void selectAll() {
		}

		@Override
		public void clearAndSelect(int index) {
		}

		@Override
		public void select(int index) {
		}

		@Override
		public void select(T obj) {
		}

		@Override
		public void clearSelection(int index) {
		}

		@Override
		public void clearSelection() {
		}

		@Override
		public boolean isSelected(int index) {
			return false;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public void selectPrevious() {
		}

		@Override
		public void selectNext() {
		}

		@Override
		public void selectFirst() {
		}

		@Override
		public void selectLast() {
		}
	}
}