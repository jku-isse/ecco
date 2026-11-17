package at.jku.isse.ecco.adapter.view;

import at.jku.isse.ecco.tree.Node;

import java.io.IOException;

/**
 * A reader whose nodes record no lines can say where they are by rendering a file's tree: the text
 * its writer would write, with the part of it each node produced. The language server locates a
 * line's node so - for a tree just read, whose rendering is the file as read when the adapter writes
 * it back exactly (it checks that the text is the same).
 */
public interface SourceRenderer {

	/**
	 * @param fileNode The node of a file, as this reader read it
	 * @return Its text with each node's span
	 * @throws IOException if it cannot be rendered here, e.g. without the Python the adapter needs
	 */
	RenderedSource render(Node fileNode) throws IOException;
}
