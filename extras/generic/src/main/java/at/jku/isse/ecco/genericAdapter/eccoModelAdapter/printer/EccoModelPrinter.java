package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.printer;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.tree.Node;

/**
 * Component that generates an artifact on basis of an ecco model and a corresponding grammar definition
 *
 * @author Michael Jahn
 */
public interface EccoModelPrinter {

    boolean printModelToFile(String filePath, Node eccoModelRoot, EccoModelBuilderStrategy builderStrategy);

    String printModelToString(Node eccoModelRoot, EccoModelBuilderStrategy builderStrategy);

}
