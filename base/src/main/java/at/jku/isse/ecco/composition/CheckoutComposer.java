package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CheckoutComposer {

    private final Configuration configuration;
    private final EvaluationStrategy evaluationStrategy;
    private final OrderSelector orderSelector;

    public CheckoutComposer(Configuration configuration, EvaluationStrategy evaluationStrategy){
        assert(configuration != null);
        assert(evaluationStrategy != null);

        this.configuration = configuration;
        this.evaluationStrategy = evaluationStrategy;
        this.orderSelector = new DefaultOrderSelector();
    }

    public Checkout composeCheckout(Node.Op mainTree, Collection<? extends Association.Op> selectedAssociations){
        assert(mainTree != null);

        Node.Op checkoutTree = mainTree.copyTree(true);
        NodeRemovalVisitor checkVisitor = new NodeRemovalVisitor(configuration, evaluationStrategy);
        checkoutTree.poTraverse(checkVisitor);
        OrderSetterVisitor orderVisitor = new OrderSetterVisitor(this.orderSelector);
        checkoutTree.traverse(orderVisitor);

        Collection<Artifact<?>> orderWarnings = this.orderSelector.getUncertainOrders();
        DependencyGraph dg = new DependencyGraph(selectedAssociations, DependencyGraph.ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS);
        Set<Association> unresolvedAssociations = new HashSet<>(dg.getAssociations());
        unresolvedAssociations.removeAll(selectedAssociations);

        Checkout checkout = new Checkout();
        checkout.setNode(checkoutTree);
        checkout.getOrderWarnings().addAll(orderWarnings);
        checkout.getUnresolvedAssociations().addAll(unresolvedAssociations);
        checkout.getSelectedAssociations().addAll(selectedAssociations);
        checkout.setConfiguration(configuration);
        return checkout;
    }

    public OrderSelector getOrderSelector(){
        return this.orderSelector;
    }
}
