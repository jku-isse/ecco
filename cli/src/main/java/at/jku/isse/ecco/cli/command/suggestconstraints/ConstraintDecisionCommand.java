package at.jku.isse.ecco.cli.command.suggestconstraints;

import at.jku.isse.ecco.cli.CliException;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.mining.AcceptedConstraints;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Accepts or rejects a constraint suggestion, as the GUI's Constraint Suggestions view does, or
 * with {@code --undo} moves it back to pending. The decision is stored in the repository, so it
 * travels with fork, pull and push. A suggestion is named as suggest-constraints lists it:
 * {@code REQUIRES A B}, {@code EXCLUDES A B} or {@code MANDATORY A}.
 */
public class ConstraintDecisionCommand implements Command {
    public static final String ACCEPT = "accept-constraint";
    public static final String REJECT = "reject-constraint";
    public static final String KIND_KEY = "kind";
    public static final String FEATURE_A_KEY = "feature";
    public static final String FEATURE_B_KEY = "other_feature";
    public static final String FLAG_UNDO = "--undo";
    public static final String UNDO_KEY = "undo";

    private final EccoService eccoService;
    private final OutWriter writer;
    private final boolean accept;

    public ConstraintDecisionCommand(EccoService eccoService, boolean accept, OutWriter writer) {
        this.eccoService = eccoService;
        this.accept = accept;
        this.writer = writer;
    }

    public ConstraintDecisionCommand(EccoService eccoService, boolean accept) {
        this(eccoService, accept, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        ConstraintMiner.Kind kind;
        try {
            kind = ConstraintMiner.Kind.valueOf(namespace.getString(KIND_KEY).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CliException("No constraint kind \"" + namespace.getString(KIND_KEY) + "\": REQUIRES, EXCLUDES or MANDATORY.");
        }
        String a = namespace.getString(FEATURE_A_KEY);
        String b = namespace.getString(FEATURE_B_KEY);
        boolean undo = Boolean.TRUE.equals(namespace.getBoolean(UNDO_KEY));

        if (kind == ConstraintMiner.Kind.MANDATORY && b != null)
            throw new CliException("MANDATORY takes one feature, not two.");
        if (kind != ConstraintMiner.Kind.MANDATORY && b == null)
            throw new CliException(kind + " takes two features.");
        // the miner names an EXCLUDES pair in alphabetical order; either order means the same
        if (kind == ConstraintMiner.Kind.EXCLUDES && a.compareTo(b) > 0) {
            String first = b;
            b = a;
            a = first;
        }

        eccoService.open();
        try {
            Set<String> features = eccoService.getRepository().getFeatures().stream().map(Feature::getName).collect(Collectors.toSet());
            for (String feature : b == null ? List.of(a) : List.of(a, b))
                if (!features.contains(feature))
                    throw new CliException("No feature named \"" + feature + "\".");

            String signature = Constraint.buildId(kind.name(), a, b);
            String text = describe(kind, a, b);
            if (undo) {
                boolean decided = AcceptedConstraints.acceptedSignatures(accept
                        ? eccoService.getRepository().getConstraints()
                        : eccoService.getRepository().getRejectedConstraints()).contains(signature);
                if (!decided) {
                    writer.println(text + " was not " + (accept ? "accepted" : "rejected") + ".");
                    return;
                }
                if (accept)
                    eccoService.unacceptConstraint(Constraint.Kind.valueOf(kind.name()), a, b);
                else
                    eccoService.unrejectConstraints(List.of(ConstraintSuggestionPreferences.parseSignature(signature)));
                writer.println(text + " is pending again.");
            } else if (accept) {
                eccoService.acceptConstraint(kind, a, b);
                boolean trusted = eccoService.acceptedSuggestions(eccoService.getRepository()).stream()
                        .anyMatch(s -> ConstraintSuggestionPreferences.signatureOf(s).equals(signature));
                writer.println("Accepted " + text + (trusted ? "." : " - not yet trusted: it is used once it holds in at least "
                        + EccoService.ACCEPTED_CONSTRAINT_MIN_WITNESS + " commits without an exception."));
            } else {
                eccoService.rejectConstraint(kind, a, b);
                writer.println("Rejected " + text + "; it is not suggested again.");
            }
        } finally {
            eccoService.close();
        }
    }

    /** As suggest-constraints prints it, without the figures. */
    static String describe(ConstraintMiner.Kind kind, String a, String b) {
        return switch (kind) {
            case REQUIRES -> "REQUIRES " + a + " -> " + b;
            case EXCLUDES -> "EXCLUDES " + a + " / " + b;
            case MANDATORY -> "MANDATORY " + a;
        };
    }
}
