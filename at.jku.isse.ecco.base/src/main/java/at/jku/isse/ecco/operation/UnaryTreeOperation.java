package at.jku.isse.ecco.operation;

import at.jku.isse.ecco.tree.Node;

/**
 * Applies a unary tree algorithm onto the given (sub-)tree with a result of type <code>T</code>.
 *
 * @param <T> The type of the object that is returned from the execution.
 * @author Hannes Thaller
 * @version 1.0
 */
public interface UnaryTreeOperation<T> {

	/**
	 * Returns the result of the unary algorithm.
	 *
	 * @param root the operand
	 * @return The result of the algorithm.
	 */
	T apply(Node root);

}