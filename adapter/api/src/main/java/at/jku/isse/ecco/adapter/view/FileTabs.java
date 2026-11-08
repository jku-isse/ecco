package at.jku.isse.ecco.adapter.view;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The files a code viewer shows, a tab for each: a commit's composition, or another tree, can hold
 * many. The C, Java, Text, Markdown and LilyPond viewers each had a copy of this.
 */
public final class FileTabs {

	private FileTabs() {
	}

	/**
	 * The files of plugin [pluginId] in the whole tree [node] is in - from its root, which a commit's
	 * composition has above its files - in the order of the tree.
	 */
	public static List<Node> files(Node node, String pluginId) {
		Node root = node;
		while (root.getParent() != null)
			root = root.getParent();
		List<Node> files = new ArrayList<>();
		collect(root, pluginId, files);
		return files;
	}

	private static void collect(Node node, String pluginId, List<Node> files) {
		// composition root/container nodes have no artifact of their own
		if (isFile(node, pluginId)) {
			files.add(node);
			// no file in a file: its lines were walked too, building every node of a lazy composition
			return;
		}
		for (Node child : node.getChildren())
			collect(child, pluginId, files);
	}

	/**
	 * The file of plugin [pluginId] [node] is in, or is, found walking up; null if none. (Not by the
	 * lines rendered: a file's node, or one that only groups others, has no line of its own.)
	 */
	public static Node fileOf(Node node, String pluginId) {
		for (Node current = node; current != null; current = current.getParent())
			if (isFile(current, pluginId))
				return current;
		return null;
	}

	private static boolean isFile(Node node, String pluginId) {
		return node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data && pluginId.equals(data.getPluginId());
	}

	/** A file's tab title: its path in the repository - by name alone two files of one name in different folders looked alike. */
	public static String label(Node file) {
		return file.getArtifact().getData() instanceof PluginArtifactData data ? data.getPath().toString() : "?";
	}

	/**
	 * What shows [items] - each a file, or what a viewer keeps of one: with several a tab for each,
	 * titled by [label], [shown] selected (else the first); else the only one's content, or [empty]'s
	 * for none. An item's [content] is made only when it is first shown: each file was rendered, on
	 * every click, which for a commit of tinycc were hundreds.
	 */
	public static <T> javafx.scene.Node show(List<T> items, T shown, Function<T, String> label, Function<T, javafx.scene.Node> content, Supplier<javafx.scene.Node> empty) {
		if (items.isEmpty())
			return empty.get();
		if (items.size() == 1)
			return content.apply(items.get(0));

		T selected = items.contains(shown) ? shown : items.get(0);
		TabPane tabPane = new TabPane();
		Map<Tab, T> notShown = new HashMap<>();
		Tab selectedTab = null;
		for (T item : items) {
			Tab tab = new Tab(label.apply(item));
			tab.setClosable(false);
			tabPane.getTabs().add(tab);
			if (item.equals(selected)) {
				tab.setContent(content.apply(item));
				selectedTab = tab;
			} else {
				notShown.put(tab, item);
			}
		}
		tabPane.getSelectionModel().select(selectedTab);
		// only now: adding the first tab selected it
		tabPane.getSelectionModel().selectedItemProperty().addListener((o, was, tab) -> {
			T item = tab == null ? null : notShown.remove(tab);
			if (item != null)
				tab.setContent(content.apply(item));
		});
		return tabPane;
	}
}
