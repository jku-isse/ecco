package at.jku.isse.ecco.cli.command.suggestconstraints;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.List;
import java.util.Set;

public class SuggestConstraintsCommand implements Command {
    public final static String SUGGEST_CONSTRAINTS = "suggest-constraints";
    public static final String FLAG_MIN_WITNESS = "--min-witness";
    public static final String MIN_WITNESS_KEY = "min_witness";
    public static final String FLAG_CONFIDENCE = "--confidence";
    public static final String CONFIDENCE_KEY = "confidence";

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

        eccoService.open();
        List<Set<String>> configs = ConfigurationBridge.readConfigurations(eccoService);
        eccoService.close();

        List<ConstraintMiner.Suggestion> suggestions =
                new ConstraintMiner(minWitness, confidence, null).mine(configs);

        if (suggestions.isEmpty()) {
            writer.println("No constraint suggestions (from " + configs.size() + " configurations).");
            return;
        }

        writer.println("Constraint suggestions from " + configs.size() + " configurations:");
        writer.println("(suggestions only -- confirm before adding to the feature model)");
        for (ConstraintMiner.Suggestion suggestion : suggestions) {
            writer.println(suggestion.toString());
        }
    }
}
