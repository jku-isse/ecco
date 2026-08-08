package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class MarkdownPlugin extends ArtifactPlugin {

	private final MarkdownModule module = new MarkdownModule();

	@Override
	public String getPluginId() {
		return MarkdownPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "MarkdownArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Markdown Artifact Plugin";
	}

}
