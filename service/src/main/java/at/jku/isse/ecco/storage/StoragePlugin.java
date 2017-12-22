package at.jku.isse.ecco.storage;

import com.google.inject.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public abstract class StoragePlugin {

	public abstract String getPluginId();

	public abstract Module getModule();

	public abstract String getName();

	public abstract String getDescription();

	public static StoragePlugin[] getDataPlugins() {
		final ServiceLoader<StoragePlugin> loader = ServiceLoader.load(StoragePlugin.class);

		List<StoragePlugin> plugins = new ArrayList<>();

		for (final StoragePlugin plugin : loader) {
			plugins.add(plugin);
		}

		return plugins.toArray(new StoragePlugin[plugins.size()]);
	}

}
