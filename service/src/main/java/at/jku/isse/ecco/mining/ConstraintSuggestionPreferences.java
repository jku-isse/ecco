package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Constraint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Persists which mined {@link ConstraintMiner.Suggestion}s a human has *rejected*, so a review UI
 * doesn't keep re-proposing the same pair. Scoped per repository (feature names are only
 * meaningful within one repository), backed by {@link Preferences}, mirroring
 * {@code at.jku.isse.ecco.service.AdapterPreferences}.
 *
 * <p>Rejection is local/personal (a "don't re-show me this" list, per-machine, lower stakes, no
 * reason to share via fork/pull/push) -- unlike acceptance, which is now a real, repository-persisted
 * entity (see {@code at.jku.isse.ecco.core.Constraint},
 * {@code EccoService#acceptConstraint}/{@code #unacceptConstraint}) precisely so it travels with the
 * repository and is visible to every collaborator, not just the machine that accepted it. Per the
 * mining epistemic contract (see CONSTRAINT_MINING_DESIGN.md): a decision recorded here or as a
 * persisted {@code Constraint} only means "a human reviewed this suggestion" -- neither is ever
 * trusted on its own without re-verification against freshly mined data (see
 * {@code EccoService#acceptedSuggestions}).
 */
public final class ConstraintSuggestionPreferences {

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

    /**
     * Stable identity for a suggestion, independent of the confidence/witness it was mined with.
     * Same format (and the same shared, encoding {@link Constraint#buildId} construction) as
     * {@code SerConstraint}'s id and {@link AcceptedConstraints#acceptedSignatures} -- all three
     * must stay byte-for-byte identical for a given kind/a/b, since freshly-mined suggestions here
     * are compared directly against persisted-constraint signatures from the other two.
     */
    public static String signatureOf(ConstraintMiner.Suggestion suggestion) {
        return Constraint.buildId(suggestion.kind.name(), suggestion.a, suggestion.b);
    }

    /** Inverse of {@link #signatureOf}; returns null for an ill-formed signature (e.g. hand-edited prefs). */
    public static AcceptedConstraint parseSignature(String signature) {
        String[] parts = signature.split("\\|", 3);
        if (parts.length != 3) return null;
        try {
            ConstraintMiner.Kind kind = ConstraintMiner.Kind.valueOf(parts[0]);
            return new AcceptedConstraint(kind, Constraint.decodeIdPart(parts[1]),
                    parts[2].isEmpty() ? null : Constraint.decodeIdPart(parts[2]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // NOTE: accepted-constraint tracking (formerly accept()/getAccepted()/getAcceptedConstraints()
    // here) moved into the repository itself -- see at.jku.isse.ecco.core.Constraint,
    // EccoService#acceptConstraint/#unacceptConstraint. Rejected-suggestion tracking stays here: a
    // personal, local "don't re-show me this" list, lower stakes, no reason to share via fork/pull/push.

    public static Set<String> getRejected(Path repositoryDir) {
        return readSet(REJECTED_KEY_PREFIX + repoScope(repositoryDir));
    }

    public static void reject(Path repositoryDir, String signature) {
        Set<String> rejected = getRejected(repositoryDir);
        rejected.add(signature);
        writeSet(REJECTED_KEY_PREFIX + repoScope(repositoryDir), rejected);
    }

    /** Moves a signature back to "pending" (out of rejected). */
    public static void clearDecision(Path repositoryDir, String signature) {
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
