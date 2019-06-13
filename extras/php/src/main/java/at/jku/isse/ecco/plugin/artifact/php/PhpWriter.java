package at.jku.isse.ecco.plugin.artifact.php;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.service.listener.WriteListener;
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

/**
 * @author Timea Kovacs
 */
public class PhpWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return PhpPlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
				/*
		 * Every node in the input is a Php file. The children of files are lines. The children of lines are characters.
		 */

		List<Path> output = new ArrayList<Path>();

		for (Node fileNode : input) {
			Artifact<PluginArtifactData> fileArtifact = (Artifact<PluginArtifactData>) fileNode.getArtifact();
			Path outputPath = base.resolve(fileArtifact.getData().getPath());
			output.add(outputPath);

			try (BufferedWriter bw = Files.newBufferedWriter(outputPath)) {
				for (Node lineNode : fileNode.getChildren()) {
					PhpArtifactData phpArtifactData = (PhpArtifactData) lineNode.getArtifact().getData();

					bw.write("<?php");
					//bw.write(phpArtifactData.toString());
					StringBuffer sb = new StringBuffer();
					this.recursiveToString(lineNode, sb);
					bw.write(sb.toString());
					bw.newLine();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return output.toArray(new Path[output.size()]);
	}


	private void recursiveToString(Node node, StringBuffer sb) {
		PhpArtifactData phpArtifactData = (PhpArtifactData) node.getArtifact().getData();

		if (phpArtifactData.getType() == PhpArtifactData.Type.BASE) {
			for (Node child : node.getChildren()) {
				this.recursiveToString(child, sb);
			}
			sb.append("\n");
		} else if (phpArtifactData.getType() == PhpArtifactData.Type.BLOCK) {
			sb.append("\n" + phpArtifactData.getValue() + " { ");
			for (Node child : node.getChildren()) {
				this.recursiveToString(child, sb);
			}
			sb.append("\n }");
		} else if (phpArtifactData.getType() == PhpArtifactData.Type.FUNCTION_OR_CLASS) {
			sb.append("\n" + phpArtifactData.getValue().replace("( )", ""));
			boolean parameters = true;
			for (Node child : node.getChildren()) {
				if (parameters) {
					sb.append(" ");
					this.recursiveToString(child, sb);
					sb.append(" \n{ ");
					parameters = false;
				} else {
					this.recursiveToString(child, sb);
				}
			}
			sb.append("\n }");
		} else if (phpArtifactData.getType() == PhpArtifactData.Type.PARAMETERS) {
			sb.append("(");
			if (node.getChildren().size() > 0) {
				for (Node child : node.getChildren()) {
					sb.append(((PhpArtifactData) child.getArtifact().getData()).getValue() + ",");
				}
				sb.deleteCharAt(sb.length() - 1);
			}
			sb.append(")");
		} else {
			sb.append("\n " + phpArtifactData.getValue());
			for (Node child : node.getChildren()) {
				this.recursiveToString(child, sb);
			}
		}

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
