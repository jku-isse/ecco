package at.jku.isse.ecco.adapter.python;

import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.adapter.view.SourceSpanViewer;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;

/** Association preview for Python files, JSON files and Jupyter notebooks - see {@link PythonRenderer}. */
public class PythonViewer extends SourceSpanViewer {

	@Override
	protected RenderedSource render(Node fileNode) throws IOException {
		return PythonRenderer.render(fileNode);
	}

	@Override
	public String getPluginId() {
		return PythonPlugin.class.getName();
	}
}
