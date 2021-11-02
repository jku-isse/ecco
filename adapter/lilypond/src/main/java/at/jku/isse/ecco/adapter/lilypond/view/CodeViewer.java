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
						if (getGraphic() != null) {
							//HBox hb = (HBox)getGraphic();
							//for (hb.getChildren())
						}

						super.updateItem(blocks, empty);

						if (empty || null == blocks) {
							setGraphic(null);
						} else {
							HBox box = new HBox();
							for (NodeTextBlock ntb : blocks) {
								Label l = new Label(ntb.getText());
								if (!ntb.isFirst() && !ntb.isLast()) {
									l.getStyleClass().add("innerBlock");
								} else if (!ntb.isLast()) {
									l.getStyleClass().add("firstBlock");
								} else if (!ntb.isFirst()) {
									l.getStyleClass().add("lastBlock");
								}
								l.setOnMouseEntered(e -> {
									l.setUserData(l.getBackground());
									l.setBackground(new Background(new BackgroundFill(Color.rgb(50, 197, 255), null, null)));
									showAssociationInfo(ntb.getAssociation());
								});
								l.setOnMouseExited(e -> {
									l.setBackground((Background) l.getUserData());
								});

								String aiId = ntb.getAssociation().getId();
								Object val = associationInfos.get(aiId).getPropertyValue("color");
								if (val instanceof Color) {
									String col = val.equals(Color.TRANSPARENT) ? "white" : "#" + val.toString().substring(2, 8);
									l.setStyle("-fx-background-color: " + col);
								}
								box.getChildren().add(l);
							}
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

	@Override
	public void showTree(Node node) {
		final Node n = node.getNode(); // in case of a wrapped node
		root = getPluginNode(n);
		if (root == null) { return; }

		PluginArtifactData pad = (PluginArtifactData)root.getArtifact().getData();
		if (!pad.getFileName().equals(currentFile)) {
			isTreeInitialized = false;
			currentFile = pad.getFileName();

			Task<Void> buildTask = new Task<>() {
				@Override
				protected Void call() {
					final ArrayList<NodeTextBlock[]> lines = new ArrayList<>();
					ArrayList<NodeTextBlock> line = new ArrayList<>();
					for (Node cn : root.getChildren()) {
						line = buildCodeLinesRec(cn, lines, line);
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
		System.out.println(sb);
		return sb.toString();
	}

	private ArrayList<NodeTextBlock> buildCodeLinesRec(Node n, Collection<NodeTextBlock[]> lines, ArrayList<NodeTextBlock> line) {
		ArtifactData d = n.getArtifact().getData();
		if (d instanceof BaseContextArtifactData) {
			for (Node cn : n.getChildren()) {
				line = buildCodeLinesRec(cn, lines, line);
			}

		} else if (d instanceof DefaultTokenArtifactData) {
			NodeTextBlock ntb = new NodeTextBlock(n);
			line.add(ntb);

			if (ntb.numLines() > 1) {
				NodeTextBlock[] l = new NodeTextBlock[line.size()];
				lines.add(line.toArray(l));

				for (int i = 1; i < ntb.numLines(); i++) {
					if (i < ntb.numLines() - 1) {
						lines.add(new NodeTextBlock[]{ntb.getGroup().get(i)});
					} else {
						line = new ArrayList<>();
						line.add(ntb.getGroup().get(i));
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
				System.out.println("color changed to " + evt.getNewValue());
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