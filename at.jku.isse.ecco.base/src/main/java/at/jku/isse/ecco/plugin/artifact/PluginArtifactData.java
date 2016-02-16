package at.jku.isse.ecco.plugin.artifact;

import java.nio.file.Path;

public class PluginArtifactData implements ArtifactData {

	private String pluginId;
	private Path path;

	protected PluginArtifactData() {
		this.pluginId = null;
		this.path = null;
	}

	public PluginArtifactData(String pluginId, Path path) {
		this.pluginId = pluginId;
		this.path = path;
	}

	public String getPluginId() {
		return this.pluginId;
	}

	public Path getFileName() {
		return this.path.getFileName();
	}

	public Path getPath() {
		return this.path;
	}

	@Override
	public String toString() {
		return this.pluginId + "(" + this.path.toString() + ")";
	}

	@Override
	public int hashCode() {
		return this.path.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PluginArtifactData that = (PluginArtifactData) o;

		return path.equals(that.path);

	}

}
