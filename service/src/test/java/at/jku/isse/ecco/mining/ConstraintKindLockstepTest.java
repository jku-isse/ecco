package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Constraint;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Constraint.Kind} (base) and {@link ConstraintMiner.Kind} (service) are independent enums
 * bridged only by name ({@code EccoService#toConstraintKind}, {@code Kind.valueOf(other.name())}) --
 * base cannot depend on service, so they can't share a single enum. This pins that they stay in
 * lockstep: a future addition to one without the other must fail here, at test time, rather than
 * silently at runtime via {@link IllegalArgumentException} the first time someone accepts a
 * suggestion of the new kind.
 */
public class ConstraintKindLockstepTest {

    @Test
    public void kindEnumsHaveIdenticalNameSets() {
        Set<String> constraintKindNames = java.util.Arrays.stream(Constraint.Kind.values())
                .map(Enum::name).collect(Collectors.toSet());
        Set<String> minerKindNames = java.util.Arrays.stream(ConstraintMiner.Kind.values())
                .map(Enum::name).collect(Collectors.toSet());
        assertEquals(minerKindNames, constraintKindNames,
                "Constraint.Kind (base) and ConstraintMiner.Kind (service) must have identical name "
                        + "sets for the name-based bridge (Kind.valueOf(other.name())) to stay safe");
    }
}
