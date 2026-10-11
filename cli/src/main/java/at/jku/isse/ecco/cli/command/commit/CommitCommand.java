package at.jku.isse.ecco.cli.command.commit;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.List;

public class CommitCommand implements Command {
    public final static String COMMIT = "commit";
    public static final String FLAG_CONFIGURATION = "-c";
    private static final String CONFIGURATION_KEY = "c";
    public static final String FLAG_COMMIT_MESSAGE = "-m";
    private static final String COMMIT_MESSAGE_KEY = "m";
    private final EccoService eccoService;
    private final OutWriter writer;

    public CommitCommand(EccoService eccoService, OutWriter writer) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public CommitCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();
        try {
            String configurationString = namespace.getString(CONFIGURATION_KEY);
            // checked before committing: only exception-free rules count, and the committed
            // configuration is the exception to every rule it violates - afterwards it violates none
            List<String> violations = this.constraintViolations(configurationString);
            Commit commit = eccoService.commit(namespace.getString(COMMIT_MESSAGE_KEY), configurationString);
            writer.println("Committed " + commit.getId() + " as " + commit.getConfiguration().getConfigurationString());
            // advisory, as in the GUI: the commit is stored either way
            for (String violation : violations)
                writer.println("CONSTRAINT: " + violation);
        } finally {
            eccoService.close();
        }
    }

    /** None if the configuration cannot be parsed: the commit reports that. */
    private List<String> constraintViolations(String configurationString) {
        Configuration configuration;
        try {
            configuration = eccoService.parseConfigurationString(configurationString);
        } catch (RuntimeException e) {
            return List.of();
        }
        return eccoService.checkConstraintViolations(configuration);
    }
}
