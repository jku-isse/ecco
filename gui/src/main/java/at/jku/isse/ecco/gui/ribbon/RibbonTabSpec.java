package at.jku.isse.ecco.gui.ribbon;

import org.kordamp.ikonli.Ikon;

import java.util.List;

/**
 * One ribbon tab - the analog of a top-level {@code Menu} - with a label, its own icon, and its
 * groups of actions (rendered as a "band" of buttons below the tab row once selected).
 */
public record RibbonTabSpec(String label, Ikon icon, List<RibbonGroup> groups) {
}
