package at.jku.isse.ecco.cli.command.designspace;

import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class MergeWorkspaces implements Command {
    private static final String MERGE = "merge";
    private static final String CONFIGURATION = "CONFIGURATION";

    private final EccoService eccoService;

    public MergeWorkspaces(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();

        String configurationString = namespace.getString(CONFIGURATION);
        Configuration configuration = eccoService.parseConfigurationString(configurationString);

        eccoService.close();
        System.out.println(configuration);
    }

    @Override
    public void register(Subparsers commandParser, CommandRegister commandRegister) {
        Subparser parser = commandParser.addParser(MERGE);

        parser.setDefault(ProgramConstants.COMMAND, MERGE);
        parser.addArgument(CONFIGURATION).required(true);
        commandRegister.register(MERGE, this);
    }
}
