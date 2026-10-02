package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/**
 * A fingerprint of what an association's minimized condition was computed from, stored next to it
 * (see {@code Association.Op#setMinimizedCondition(String, String)}). A minimized condition is only
 * trusted while the fingerprint still matches:
 * <ul>
 * <li>the association's condition: it changes with later commits, also ones that don't touch the
 * association, since the condition depends on repository-wide counts;</li>
 * <li>the distinct committed configurations and the accepted constraints, from which the feature
 * model the minimization relied on is mined. Only a new, distinct configuration can remove a mined
 * hard constraint (as a counterexample); more witnesses only add constraints, under which the old
 * minimization stays equivalent.</li>
 * </ul>
 */
public final class MinimizationBasis {

    private MinimizationBasis() {
    }

    /** The part shared by every association: distinct configurations and accepted constraint signatures. */
    public static String ofRepository(Collection<Set<String>> configurations, Collection<String> acceptedSignatures) {
        StringBuilder sb = new StringBuilder("configurations\n");
        Set<String> distinct = new TreeSet<>();
        for (Set<String> configuration : configurations)
            distinct.add(String.join(",", new TreeSet<>(configuration)));
        for (String configuration : distinct)
            sb.append(configuration).append('\n');
        sb.append("constraints\n");
        for (String signature : new TreeSet<>(acceptedSignatures))
            sb.append(signature).append('\n');
        return sha256(sb.toString());
    }

    public static String of(Association association, String repositoryBasis) {
        return sha256(association.computeCondition().toLogicString() + "\n" + repositoryBasis);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
