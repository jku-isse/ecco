package at.jku.isse.ecco.exporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.exceptions.WrongArtifactDataTypeException;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.Node;

/**
 * 
 * @author Simon Eilmsteiner
 *
 */
public class TraceExporter {

	private final PresenceCondition condition;
	private final Path toPath;

	public TraceExporter(Association a, Path toPath) throws WrongArtifactDataTypeException {
		if (a == null || toPath == null || a.getRootNode() == null || a.getPresenceCondition() == null)
			throw new IllegalArgumentException("The argument(s) cannot be null");
		this.condition = a.getPresenceCondition();
		this.toPath = toPath;

		for (Node node : a.getRootNode().getChildren().get(0).getChildren())
			processPluginNode(node);
	}

	private void processPluginNode(Node node) throws WrongArtifactDataTypeException {
		try {
			PluginArtifactData pad = (PluginArtifactData) node.getArtifact().getData();
			if (!pad.getFileName().toString().endsWith(".txt"))
				return;
			Files.copy(pad.getPath(), toPath.resolve(pad.getFileName()), StandardCopyOption.REPLACE_EXISTING);

			// process children
			for (Node n : node.getChildren()) {
				processLineNode(n); // TODO kann Child nur LineNode sein?
			}
		} catch (ClassCastException e) {
			throw new WrongArtifactDataTypeException("Wrong Type"); // TODO
																	// bessere
																	// Fehlermeldung
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processLineNode(Node node) throws WrongArtifactDataTypeException {
		try {
			LineArtifactData lad = (LineArtifactData) node.getArtifact().getData();
			// TODO
			System.out.println("LineAD");
		} catch (ClassCastException e) {
			throw new WrongArtifactDataTypeException("Wrong Type"); // TODO
																	// bessere
																	// Fehlermeldung
		}

		// process children, but there must not be children.
		for (Node n : node.getChildren()) {
			// processLineNode(n);
			System.err.println("A Line Node must not have children. \n" + n.toString());
		}
	}
}
