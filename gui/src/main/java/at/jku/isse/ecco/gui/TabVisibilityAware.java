package at.jku.isse.ecco.gui;

/**
 * Implemented by views that skip expensive per-commit work while they aren't the one currently
 * shown - see {@code MainView#switchTo} (calls this on the outgoing and incoming view whenever the
 * menu-driven content area swaps) and each implementor's own {@code setTabVisible} javadoc for the
 * full rationale (named for the {@code Tab.selectedProperty()} listener this replaced).
 */
public interface TabVisibilityAware {

	void setTabVisible(boolean visible);

}
