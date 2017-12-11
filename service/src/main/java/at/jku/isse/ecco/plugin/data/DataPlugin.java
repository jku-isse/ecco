package at.jku.isse.ecco.plugin.data;

import com.google.inject.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public abstract class DataPlugin {

	public abstract String getPluginId();

	public abstract Module getModule();

	public abstract String getName();

	public abstract String getDescription();

	public static DataPlugin[] getDataPlugins() {
		final ServiceLoader<DataPlugin> loader = ServiceLoader.load(DataPlugin.class);

		List<DataPlugin> plugins = new ArrayList<DataPlugin>();

		for (final DataPlugin plugin : loader) {
			plugins.add(plugin);
		}

		return plugins.toArray(new DataPlugin[plugins.size()]);
	}

}
