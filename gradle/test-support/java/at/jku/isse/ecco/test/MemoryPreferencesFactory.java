package at.jku.isse.ecco.test;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Preferences of a test JVM, held in memory: tests write recent repositories, disabled adapters and
 * other settings, which went to the user's own (on a Mac ~/Library/Preferences/at.jku.isse*.plist) -
 * seen by a running ECCO and by tests running in parallel elsewhere. Every test task uses it
 * (java.util.prefs.PreferencesFactory in the root build.gradle); each subproject compiles it with
 * its tests.
 */
public final class MemoryPreferencesFactory implements PreferencesFactory {

	private static final Preferences USER = new Node(null, "");
	private static final Preferences SYSTEM = new Node(null, "");

	@Override
	public Preferences userRoot() {
		return USER;
	}

	@Override
	public Preferences systemRoot() {
		return SYSTEM;
	}

	private static final class Node extends AbstractPreferences {

		private final Map<String, String> values = new HashMap<>();
		private final Map<String, Node> children = new HashMap<>();

		Node(Node parent, String name) {
			super(parent, name);
		}

		@Override
		protected void putSpi(String key, String value) {
			this.values.put(key, value);
		}

		@Override
		protected String getSpi(String key) {
			return this.values.get(key);
		}

		@Override
		protected void removeSpi(String key) {
			this.values.remove(key);
		}

		@Override
		protected void removeNodeSpi() {
			((Node) this.parent()).children.remove(this.name());
		}

		@Override
		protected String[] keysSpi() {
			return this.values.keySet().toArray(new String[0]);
		}

		@Override
		protected String[] childrenNamesSpi() {
			return this.children.keySet().toArray(new String[0]);
		}

		@Override
		protected AbstractPreferences childSpi(String name) {
			return this.children.computeIfAbsent(name, n -> new Node(this, n));
		}

		@Override
		protected void syncSpi() {
		}

		@Override
		protected void flushSpi() {
		}
	}
}
