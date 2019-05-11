package at.jku.isse.ecco.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.core.MemAssociation;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;

public class TraceImporter {

	public static void importTrace(Repository repository, Path fromPath) {
		List<Association> associations = new ArrayList<>();
		List<Artifact<PluginArtifactData>> pluginArtifacts = new ArrayList<>();
		try {
			Files.walk(fromPath).filter(file -> file.toString().endsWith(".txt")).forEach(file -> {
				Association association = new MemAssociation();
				PluginArtifactData pad = new PluginArtifactData("1", file);
				Artifact.Op<PluginArtifactData> pluginArtifact = new MemArtifact<>(pad);
				PartialOrderGraph.Op sequenceGraph = new MemPartialOrderGraph();
				try {
					sequenceGraph.merge(Files.lines(file).filter(line -> !line.matches("^((\\s)*(#if (.)*|#endif))"))
							.map(line -> new MemArtifact<LineArtifactData>(new LineArtifactData(line)))
							.collect(Collectors.toList()));
				} catch (IOException e) {
					e.printStackTrace();
				} // TODO diese liste aufbauen
				pluginArtifact.setSequenceGraph(sequenceGraph);
				// TODO read file
				// TODO set association stuff
				pluginArtifacts.add(pluginArtifact);
			});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
