package at.jku.isse.ecco.adapter.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.paint.Color;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The colours a code viewer gives its lines: each association's, from the {@link AssociationInfo}s
 * the Artifacts view sets - its colour while it is selected, white otherwise - and the details shown
 * when the mouse is over one of its lines. The C, Java, Text, Markdown and LilyPond viewers each had
 * a copy of this.
 */
public final class AssociationColors {

	/** The colour of a line no selected association colours. */
	public static final Color NONE = Color.WHITE;

	private final Map<String, AssociationInfo> infos = new HashMap<>();
	private final Map<String, PropertyChangeListener> listeners = new HashMap<>();
	private final BiConsumer<String, Color> recolor;

	/**
	 * [recolor] is told when a selected association's colour changes - its id and the new colour - to
	 * colour its lines anew.
	 */
	public AssociationColors(BiConsumer<String, Color> recolor) {
		this.recolor = recolor;
	}

	/** Takes [associationInfos] (may be null: none) in place of those before, which are no longer listened to. */
	public void set(Collection<AssociationInfo> associationInfos) {
		for (Map.Entry<String, AssociationInfo> entry : this.infos.entrySet())
			entry.getValue().removePropertyChangeListener(this.listeners.get(entry.getKey()));
		this.infos.clear();
		this.listeners.clear();
		if (associationInfos == null)
			return;
		for (AssociationInfo info : associationInfos)
			this.infos.put(info.getAssociation().getId(), info);
		for (AssociationInfo info : this.infos.values()) {
			PropertyChangeListener listener = evt -> {
				if ("color".equals(evt.getPropertyName()) && selected(info))
					this.recolor.accept(info.getAssociation().getId(), (Color) evt.getNewValue());
			};
			info.addPropertyChangeListener(listener);
			this.listeners.put(info.getAssociation().getId(), listener);
		}
	}

	/** [association]'s colour (may be null): set and not transparent while it is selected, else {@link #NONE}. */
	public Color colorFor(Association association) {
		if (association != null) {
			AssociationInfo info = this.infos.get(association.getId());
			if (info != null && selected(info) && info.getPropertyValue("color") instanceof Color color && !color.equals(Color.TRANSPARENT))
				return color;
		}
		return NONE;
	}

	/** The colour of [node]'s association - see {@link #associationOf}. */
	public Color colorFor(Node node) {
		return this.colorFor(associationOf(node));
	}

	/**
	 * What is shown when the mouse is over a line of [association]: its id and condition, short and
	 * in full. The association's info brings it, read by its view in the background, holding the
	 * service's lock (property "hoverText"); without one, the id alone. It was computed here then, on
	 * the FX thread, while a commit running meanwhile changed the counters under it.
	 */
	public String hoverText(Association association) {
		AssociationInfo info = this.infos.get(association.getId());
		if (info != null && info.getPropertyValue("hoverText") instanceof String text)
			return text;
		return association.getId();
	}

	/** The hover text of association [id] with [condition] - see {@link #hoverText(Association)}. */
	public static String hoverText(String id, Condition condition) {
		return id + " (" + condition.toDisplayString(false) + ")\n" + condition.toDisplayString();
	}

	/** The association [node]'s artifact belongs to, null if none (e.g. a node of a composition). */
	public static Association associationOf(Node node) {
		return node.getArtifact() != null && node.getArtifact().getContainingNode() != null
				? node.getArtifact().getContainingNode().getContainingAssociation() : null;
	}

	private static boolean selected(AssociationInfo info) {
		return Boolean.TRUE.equals(info.getPropertyValue("selected"));
	}
}
