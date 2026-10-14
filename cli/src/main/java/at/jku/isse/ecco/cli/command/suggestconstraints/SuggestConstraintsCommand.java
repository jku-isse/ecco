package at.jku.isse.ecco.cli.command.suggestconstraints;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.mining.AcceptedConstraints;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lists the constraint suggestions mined from the committed configurations that are not decided
 * yet - accepted or rejected ones (accept-constraint, reject-constraint, or the GUI) are left out,
 * as in the GUI; with --all they are listed after the pending ones.
 */
public class SuggestConstraintsCommand implements Command {
    public final static String SUGGEST_CONSTRAINTS = "suggest-constraints";
    public static final String FLAG_MIN_WITNESS = "--min-witness";
    public static final String MIN_WITNESS_KEY = "min_witness";
    public static final String FLAG_CONFIDENCE = "--confidence";
    public static final String CONFIDENCE_KEY = "confidence";
    public static final String FLAG_ALL = "--all";
    public static final String ALL_KEY = "all";

    private final EccoService eccoService;
    private final OutWriter writer;

    public SuggestConstraintsCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public SuggestConstraintsCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        int minWitness = namespace.getInt(MIN_WITNESS_KEY);
        double confidence = namespace.getDouble(CONFIDENCE_KEY);
        boolean all = Boolean.TRUE.equals(namespace.getBoolean(ALL_KEY));

        List<Set<String>> configs;
        Set<String> accepted;
        Set<String> rejected;
        Set<String> trusted;
        eccoService.open();
        try {
            // rejections kept per machine by earlier GUI versions (a no-op once they are moved)
            eccoService.moveLocalRejectionsIntoRepository();
            configs = ConfigurationBridge.readConfigurations(eccoService);
            accepted = AcceptedConstraints.acceptedSignatures(eccoService.getRepository().getConstraints());
            rejected = AcceptedConstraints.acceptedSignatures(eccoService.getRepository().getRejectedConstraints());
            trusted = accepted.isEmpty() ? Set.of() : eccoService.acceptedSuggestions(eccoService.getRepository()).stream()
                    .map(ConstraintSuggestionPreferences::signatureOf).collect(Collectors.toSet());
        } finally {
            eccoService.close();
        }

        List<ConstraintMiner.Suggestion> pending = new ConstraintMiner(minWitness, confidence, null).mine(configs).stream()
                .filter(s -> !accepted.contains(ConstraintSuggestionPreferences.signatureOf(s))
                        && !rejected.contains(ConstraintSuggestionPreferences.signatureOf(s)))
                .toList();

        String decided = accepted.isEmpty() && rejected.isEmpty() ? ""
                : " (" + accepted.size() + " accepted and " + rejected.size() + " rejected " + (all ? "listed below" : "not listed; --all lists them") + ")";
        if (pending.isEmpty()) {
            writer.println("No constraint suggestions (from " + configs.size() + " configurations)." + decided);
        } else {
            writer.println("Constraint suggestions from " + configs.size() + " configurations:" + decided);
            writer.println("(suggestions only -- accept-constraint or reject-constraint KIND FEATURE [OTHER_FEATURE] decides one)");
            for (ConstraintMiner.Suggestion suggestion : pending) {
                writer.println(suggestion.toString());
            }
        }

        if (all) {
            if (!accepted.isEmpty()) {
                writer.println("Accepted:");
                for (String signature : sorted(accepted))
                    writer.println("  " + describe(signature) + (trusted.contains(signature) ? "   [trusted]"
                            : "   [not yet trusted: needs " + EccoService.ACCEPTED_CONSTRAINT_MIN_WITNESS + " commits without an exception]"));
            }
            if (!rejected.isEmpty()) {
                writer.println("Rejected:");
                for (String signature : sorted(rejected))
                    writer.println("  " + describe(signature));
            }
        }
    }

    private static List<String> sorted(Set<String> signatures) {
        return signatures.stream().sorted().toList();
    }

    private static String describe(String signature) {
        ConstraintSuggestionPreferences.AcceptedConstraint constraint = ConstraintSuggestionPreferences.parseSignature(signature);
        return constraint == null ? signature : ConstraintDecisionCommand.describe(constraint.kind, constraint.a, constraint.b);
    }
}
