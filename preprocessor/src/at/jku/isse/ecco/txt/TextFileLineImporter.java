package at.jku.isse.ecco.txt;

import java.util.ArrayList;
import java.util.List;

import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.importer.LineImporter;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.tree.MemNode;

public class TextFileLineImporter implements LineImporter {
	
	private List<MemArtifact<LineArtifactData>> list = new ArrayList<>();

	@Override
	public void importLine(String line, MemNode actualPluginNode, PartialOrderGraph.Op pog) {
		MemArtifact<LineArtifactData> lineArtifact = new MemArtifact<LineArtifactData>(new LineArtifactData(line));
		list.add(lineArtifact);
		MemNode node = new MemNode(lineArtifact);
		node.setUnique(true);
		actualPluginNode.addChild(node);
		
		pog.merge(list);
	}

}
