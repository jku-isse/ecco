package at.jku.isse.ecco.gui;

/** Display names for adapter plugins in the read/write logs of the commit, checkout and import views. */
public final class PluginNames {

	private PluginNames() {
	}

	/**
	 * getPluginId() returns a fully-qualified class name (e.g.
	 * "at.jku.isse.ecco.adapter.lilypond.LilypondPlugin") - just the simple class name is enough to
	 * display.
	 */
	public static String shortName(String pluginId) {
		if (pluginId == null) return null;
		int lastDot = pluginId.lastIndexOf('.');
		return lastDot < 0 ? pluginId : pluginId.substring(lastDot + 1);
	}
}
