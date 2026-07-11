package at.jku.isse.ecco.cli.command.minimizepreview;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.mining.FeatureModelFormula;
import at.jku.isse.ecco.mining.ModuleConditionBridge;
import at.jku.isse.ecco.mining.PresenceConditionMinimizer;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.logicng.formulas.Formula;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Read-only preview of what each association's presence condition would look like if simplified
 * using today's <em>accepted, hard</em> constraints -- never writes anything back. See
 * CONSTRAINT_MINING_DESIGN.md's "Minimization" section: this is intentionally a display/analysis
 * command, not something that rewrites the repository, since a wrong accepted constraint here would
 * only mislead a preview rather than corrupt real data.
 */
public class MinimizePreviewCommand implements Command {
    public final static String MINIMIZE_PREVIEW = "minimize-preview";
    public static final String ID_KEY = "id";
    public static final String FLAG_MIN_WITNESS = "--min-witness";
    public static final String MIN_WITNESS_KEY = "min_witness";
    public static final String FLAG_CONFIDENCE = "--confidence";
    public static final String CONFIDENCE_KEY = "confidence";

    private final EccoService eccoService;
    private final OutWriter writer;

    public MinimizePreviewCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public MinimizePreviewCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        String id = namespace.getString(ID_KEY);
        int minWitness = namespace.getInt(MIN_WITNESS_KEY);
        double confidence = namespace.getDouble(CONFIDENCE_KEY);

        eccoService.open();

        List<Set<String>> configs = ConfigurationBridge.readConfigurations(eccoService);
        List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(minWitness, confidence, null).mine(configs);
        Set<String> accepted = ConstraintSuggestionPreferences.getAccepted(eccoService.getRepositoryDir());

        List<ConstraintMiner.Suggestion> acceptedSuggestions = new ArrayList<>();
        for (ConstraintMiner.Suggestion suggestion : mined) {
            if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
                acceptedSuggestions.add(suggestion);
            }
        }
        Formula featureModel = FeatureModelFormula.compile(acceptedSuggestions);
        long hardCount = acceptedSuggestions.stream().filter(ConstraintMiner.Suggestion::isHard).count();

        writer.println("Feature model: " + hardCount + " accepted hard constraint(s) compiled (of "
                + accepted.size() + " accepted total; near-misses and stale/unreproducible ones are excluded).");
        writer.println("");

        for (Association association : eccoService.getRepository().getAssociations()) {
            if (id != null && !association.getId().equals(id)) continue;

            Condition condition = association.computeCondition();
            List<PresenceConditionMinimizer.Term> originalTerms = ModuleConditionBridge.toTerms(condition);
            List<PresenceConditionMinimizer.Term> minimizedTerms = PresenceConditionMinimizer.minimize(featureModel, originalTerms);

            String originalString = PresenceConditionMinimizer.format(originalTerms);
            String minimizedString = PresenceConditionMinimizer.format(minimizedTerms);

            writer.println("[" + association.getId() + "] " + (originalString.equals(minimizedString) ? "(unchanged)" : "(simplified)"));
            writer.println("  original:  " + originalString);
            writer.println("  minimized: " + minimizedString);
        }

        eccoService.close();
    }
}