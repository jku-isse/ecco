package at.jku.isse.ecco.txt;

import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.importer.LineImporter;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.tree.MemNode;

public class TextFileLineImporter implements LineImporter {

	@Override
	public void importLine(String line, MemNode actualPluginNode, PartialOrderGraph pog) {
		MemNode node = new MemNode(new MemArtifact<LineArtifactData>(new LineArtifactData(line)));
		node.setUnique(true);
		actualPluginNode.addChild(node);
		
		//TODO POG

	}

}
