package at.jku.isse.ecco.operation;

import at.jku.isse.ecco.tree.Node;

/**
 * Makes a string representation out of the tree and returns is.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class ToString extends AbstractUnaryTreeOperation<String> {

	private final StringBuffer buffer = new StringBuffer();
	private final StringBuffer indent = new StringBuffer();

	@Override
	protected void finalizeResult() {
		result = buffer.toString();
	}

	@Override
	protected void prefix(final Node node) {
		indent.append("\t");
		buffer.append(indent.toString()).append(node.getArtifact()).append("\n");
	}

	@Override
	protected void postfix(final Node node) {
		indent.deleteCharAt(0);
	}

}
