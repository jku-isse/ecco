package at.jku.isse.ecco.importer;

import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.tree.MemNode;

public interface LineImporter {
	public void importLine(String line, MemNode actualPluginNode, PartialOrderGraph pog);
}
