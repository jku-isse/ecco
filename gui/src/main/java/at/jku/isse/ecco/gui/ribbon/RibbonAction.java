package at.jku.isse.ecco.gui.ribbon;

import org.kordamp.ikonli.Ikon;

/**
 * One ribbon button: a label, an icon, the action to run when clicked, whether it should be
 * disabled while no repository is open, and what its tooltip says (none if null) - mirrors what {@code MainView}'s old
 * {@code requiresOpenRepository} list tracked per {@code MenuItem}.
 */
public record RibbonAction(String label, Ikon icon, Runnable action, boolean requiresOpenRepository, String tooltip) {

	public RibbonAction(String label, Ikon icon, Runnable action, boolean requiresOpenRepository) {
		this(label, icon, action, requiresOpenRepository, null);
	}
}
