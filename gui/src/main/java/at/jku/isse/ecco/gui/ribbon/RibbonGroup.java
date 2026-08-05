package at.jku.isse.ecco.gui.ribbon;

import java.util.List;

/**
 * One separator-delimited cluster of actions within a ribbon tab's band - the direct analog of a
 * {@code SeparatorMenuItem}-delimited group within one of today's dropdown {@code Menu}s.
 */
public record RibbonGroup(List<RibbonAction> actions) {
}
