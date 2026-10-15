package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The logic variables of features and revisions, and the assignment of a configuration, named in one place. */
public class FeatureLiteralsTest {

    @Test
    public void namesAndIdsBecomeVariables() {
        SerFeature cart = new SerFeature("f-1", "shopping-cart.v2");
        FeatureRevision revision = cart.addRevision("1a2b-3c");
        assertEquals("shopping_cart_v2", FeatureLiterals.featureVariable(cart));
        assertEquals("shopping_cart_v2_1a2b_3c", FeatureLiterals.revisionVariable(revision));
        assertEquals(FeatureLiterals.revisionVariable(revision), revision.getLogicLiteralRepresentation(), "what conditions are written with");
        assertEquals("(a_b & ~c_d)", FeatureLiterals.sanitize("(a.b & ~c-d)"));
        String clean = "(a_b & c)";
        assertEquals(clean, FeatureLiterals.sanitize(clean));
    }

    @Test
    public void aConfigurationMakesItsRevisionsAndFeaturesTrue() {
        SerFeature a = new SerFeature("a", "A"), b = new SerFeature("b", "B-x");
        Configuration configuration = new at.jku.isse.ecco.storage.ser.feature.SerConfiguration(new FeatureRevision[]{a.addRevision("1"), b.addRevision("2")});
        Set<String> trueVariables = configuration.toAssignment().positiveVariables().stream().map(Object::toString).collect(Collectors.toSet());
        assertEquals(Set.of("A", "A_1", "B_x", "B_x_2"), trueVariables);
    }
}
