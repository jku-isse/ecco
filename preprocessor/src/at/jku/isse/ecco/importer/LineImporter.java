package at.jku.isse.ecco.importer;

import at.jku.isse.ecco.tree.Node;

public interface LineImporter {
	public void importLine(String line, Node.Op actualPluginNode);
}
