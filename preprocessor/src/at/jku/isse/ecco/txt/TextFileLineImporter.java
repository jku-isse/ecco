package at.jku.isse.ecco.txt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.importer.LineImporter;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.tree.MemNode;

public class TextFileLineImporter implements LineImporter {
	
	private Map<MemArtifact<PluginArtifactData>, List<MemArtifact<LineArtifactData>>> map = new HashMap<>();

	@SuppressWarnings("unchecked")
	@Override
	public void importLine(String line, MemNode actualPluginNode, PartialOrderGraph.Op pog) {
		MemArtifact<LineArtifactData> lineArtifact = new MemArtifact<LineArtifactData>(new LineArtifactData(line));
		List<MemArtifact<LineArtifactData>> list = map.get(actualPluginNode.getArtifact());
		if(list == null) {
			list = new ArrayList<>();
			map.put((MemArtifact<PluginArtifactData>) actualPluginNode.getArtifact(), list);
		}		
		list.add(lineArtifact);
		MemNode node = new MemNode(lineArtifact);
		node.setUnique(true);
		lineArtifact.setContainingNode(node);
		actualPluginNode.addChild(node);
		
		pog.merge(list);
	}
	
	

}
