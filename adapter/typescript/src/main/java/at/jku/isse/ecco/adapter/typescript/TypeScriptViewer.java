package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.adapter.view.SourceSpanViewer;
import at.jku.isse.ecco.tree.Node;

/** Association preview for TypeScript/JavaScript files: their text as TypeScriptWriter writes it. */
public class TypeScriptViewer extends SourceSpanViewer {

	@Override
	protected RenderedSource render(Node fileNode) {
		return TypeScriptWriter.render(fileNode);
	}

	@Override
	public String getPluginId() {
		return TypeScriptPlugin.class.getName();
	}
}
