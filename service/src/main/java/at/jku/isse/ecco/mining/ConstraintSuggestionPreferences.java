package at.jku.isse.ecco.mining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Persists which mined {@link ConstraintMiner.Suggestion}s a human has reviewed, so a review UI
 * doesn't keep re-proposing the same pair. Scoped per repository (feature names are only
 * meaningful within one repository), backed by {@link Preferences}, mirroring
 * {@code at.jku.isse.ecco.service.AdapterPreferences}.
 *
 * <p>Per the mining epistemic contract (see CONSTRAINT_MINING_DESIGN.md): "accepted" here means
 * only "a human reviewed this and agrees it looks right" -- it records the decision, it does not
 * write a constraint into any feature model (no such persisted concept exists yet).
 */
public final class ConstraintSuggestionPreferences {

    private static final String ACCEPTED_KEY_PREFIX = "acceptedConstraintSuggestions:";
    private static final String REJECTED_KEY_PREFIX = "rejectedConstraintSuggestions:";
    private static final String SEPARATOR = ",";

    private ConstraintSuggestionPreferences() {
    }

    /** The kind/a/b identity an accepted or rejected signature decodes back into. */
    public static final class AcceptedConstraint {
        public final ConstraintMiner.Kind kind;
        public final String a;
        public final String b; // null for MANDATORY

        AcceptedConstraint(ConstraintMiner.Kind kind, String a, String b) {
            this.kind = kind;
            this.a = a;
            this.b = b;
        }
    }

    /** Stable identity for a suggestion, independent of the confidence/witness it was mined with. */
    public static String signatureOf(ConstraintMiner.Suggestion suggestion) {
        return suggestion.kind.name() + "|" + suggestion.a + "|" + (suggestion.b == null ? "" : suggestion.b);
    }

    /** Inverse of {@link #signatureOf}; returns null for an ill-formed signature (e.g. hand-edited prefs). */
    public static AcceptedConstraint parseSignature(String signature) {
        String[] parts = signature.split("\\|", 3);
        if (parts.length != 3) return null;
        try {
            ConstraintMiner.Kind kind = ConstraintMiner.Kind.valueOf(parts[0]);
            return new AcceptedConstraint(kind, parts[1], parts[2].isEmpty() ? null : parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Set<String> getAccepted(Path repositoryDir) {
        return readSet(ACCEPTED_KEY_PREFIX + repoScope(repositoryDir));
    }

    /** Accepted signatures, decoded back into kind/a/b -- e.g. for drawing them on the feature graph. */
    public static List<AcceptedConstraint> getAcceptedConstraints(Path repositoryDir) {
        List<AcceptedConstraint> constraints = new ArrayList<>();
        for (String signature : getAccepted(repositoryDir)) {
            AcceptedConstraint constraint = parseSignature(signature);
            if (constraint != null) constraints.add(constraint);
        }
        return constraints;
    }

    public static Set<String> getRejected(Path repositoryDir) {
        return readSet(REJECTED_KEY_PREFIX + repoScope(repositoryDir));
    }

    public static void accept(Path repositoryDir, String signature) {
        Set<String> rejected = getRejected(repositoryDir);
        rejected.remove(signature);
        writeSet(REJECTED_KEY_PREFIX + repoScope(repositoryDir), rejected);

        Set<String> accepted = getAccepted(repositoryDir);
        accepted.add(signature);
        writeSet(ACCEPTED_KEY_PREFIX + repoScope(repositoryDir), accepted);
    }

    public static void reject(Path repositoryDir, String signature) {
        Set<String> accepted = getAccepted(repositoryDir);
        accepted.remove(signature);
        writeSet(ACCEPTED_KEY_PREFIX + repoScope(repositoryDir), accepted);

        Set<String> rejected = getRejected(repositoryDir);
        rejected.add(signature);
        writeSet(REJECTED_KEY_PREFIX + repoScope(repositoryDir), rejected);
    }

    /** Moves a signature back to "pending" (out of both accepted and rejected). */
    public static void clearDecision(Path repositoryDir, String signature) {
        Set<String> accepted = getAccepted(repositoryDir);
        accepted.remove(signature);
        writeSet(ACCEPTED_KEY_PREFIX + repoScope(repositoryDir), accepted);

        Set<String> rejected = getRejected(repositoryDir);
        rejected.remove(signature);
        writeSet(REJECTED_KEY_PREFIX + repoScope(repositoryDir), rejected);
    }

    private static Set<String> readSet(String key) {
        String stored = prefs().get(key, "");
        if (stored.isEmpty()) return new HashSet<>();
        return Arrays.stream(stored.split(SEPARATOR)).collect(Collectors.toCollection(HashSet::new));
    }

    private static void writeSet(String key, Set<String> values) {
        Preferences prefs = prefs();
        prefs.put(key, String.join(SEPARATOR, values));
        try {
            // without an explicit flush, a write is only guaranteed to reach the backing store
            // asynchronously -- fine within one long-running GUI session (reads see the in-memory
            // value immediately regardless), but a short-lived process (e.g. the CLI) started right
            // after can race the async sync and see stale/empty data.
            prefs.flush();
        } catch (BackingStoreException e) {
            throw new RuntimeException("Failed to persist constraint suggestion preferences", e);
        }
    }

    private static String repoScope(Path repositoryDir) {
        // toRealPath(), not just toAbsolutePath().normalize(): the two can legitimately disagree on
        // the exact same physical directory. E.g. on macOS, cd'ing into a /tmp/... path and starting
        // a JVM there reports user.dir as the symlink-resolved /private/tmp/... -- a relative "."
        // (as the CLI's EccoService uses) then resolves differently than an already-absolute
        // /tmp/... Path built elsewhere (e.g. the GUI), landing in a different preferences bucket
        // for what is really the same repository.
        Path canonical;
        try {
            canonical = repositoryDir.toRealPath();
        } catch (IOException e) {
            canonical = repositoryDir.toAbsolutePath().normalize();
        }
        return Integer.toHexString(canonical.toString().hashCode());
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(ConstraintSuggestionPreferences.class);
    }
}
