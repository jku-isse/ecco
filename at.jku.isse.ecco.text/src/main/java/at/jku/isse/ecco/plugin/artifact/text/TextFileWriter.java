package at.jku.isse.ecco.plugin.artifact.text;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.listener.WriteListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class TextFileWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		/*
		 * Every node in the input is a text file. The children of files are lines. The children of lines are characters.
		 */

		System.out.println("BASE: " + base);

		List<Path> output = new ArrayList<Path>();

		for (Node fileNode : input) {
			Artifact<PluginArtifactData> fileArtifact = (Artifact<PluginArtifactData>) fileNode.getArtifact();
			Path outputPath = base.resolve(fileArtifact.getData().getPath());
			output.add(outputPath);

			try (BufferedWriter bw = Files.newBufferedWriter(outputPath)) {
				for (Node lineNode : fileNode.getChildren()) {
					LineArtifactData lineArtifactData = (LineArtifactData) lineNode.getArtifact().getData();

					bw.write(lineArtifactData.getLine());
					bw.newLine();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return output.toArray(new Path[output.size()]);
	}


	private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
