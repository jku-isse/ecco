package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * CheckoutComposer.composeCheckout() itself is a straight-line method (its complexity was already
 * mostly closed as a side effect of RepositoryOpExtractTest#composeAfterExtractReturnsTheCommittedContent
 * exercising it end-to-end) - what was actually missing (see the coverageSummary Gradle task) was
 * narrower than "test compose more": getOrderSelector() was never called by any test at all, and the
 * assert() null-checks in the constructor/composeCheckout() never had their failure path exercised.
 * Gradle's Test task runs with assertions enabled by default, so those asserts are real, reachable
 * branches here, not silently-disabled no-ops.
 */
public class CheckoutComposerTest {

    private final EntityFactory ef = new SerEntityFactory();

    @Test
    public void getOrderSelectorReturnsANonNullSelector() {
        CheckoutComposer composer = new CheckoutComposer(mock(Configuration.class), mock(EvaluationStrategy.class));

        assertNotNull(composer.getOrderSelector());
        assertInstanceOf(DefaultOrderSelector.class, composer.getOrderSelector());
    }

    @Test
    public void getOrderSelectorReturnsTheSameInstanceUsedDuringComposition() {
        CheckoutComposer composer = new CheckoutComposer(mock(Configuration.class), mock(EvaluationStrategy.class));
        OrderSelector selectorBeforeCompose = composer.getOrderSelector();

        Node.Op mainTree = ef.createRootNode();
        composer.composeCheckout(mainTree, List.of());

        assertSame(selectorBeforeCompose, composer.getOrderSelector());
    }

    @Test
    public void constructorRejectsANullConfiguration() {
        assertThrows(AssertionError.class, () -> new CheckoutComposer(null, mock(EvaluationStrategy.class)));
    }

    @Test
    public void constructorRejectsANullEvaluationStrategy() {
        assertThrows(AssertionError.class, () -> new CheckoutComposer(mock(Configuration.class), null));
    }

    @Test
    public void composeCheckoutRejectsANullMainTree() {
        CheckoutComposer composer = new CheckoutComposer(mock(Configuration.class), mock(EvaluationStrategy.class));

        assertThrows(AssertionError.class, () -> composer.composeCheckout(null, List.<Association.Op>of()));
    }

    @Test
    public void composeCheckoutOfAnEmptyTreeProducesAnEmptyCheckout() {
        CheckoutComposer composer = new CheckoutComposer(mock(Configuration.class), mock(EvaluationStrategy.class));
        Node.Op mainTree = ef.createRootNode();

        Checkout checkout = composer.composeCheckout(mainTree, List.of());

        assertNotNull(checkout.getNode());
        assertTrue(checkout.getSelectedAssociations().isEmpty());
        assertTrue(checkout.getUnresolvedAssociations().isEmpty());
    }
}
