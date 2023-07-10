package at.jku.isse.ecco.cli.command.commit;

import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class CommitCommand implements Command {
    public final static String COMMIT = "commit";
    public static final String FLAG_CONFIGURATION = "-c";
    private static final String CONFIGURATION_KEY = "c";
    public static final String FLAG_COMMIT_MESSAGE = "-m";
    private static final String COMMIT_MESSAGE_KEY = "m";
    private final EccoService eccoService;

    public CommitCommand(
            EccoService eccoService
    ) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        System.out.println(namespace.toString());
        eccoService.open();
        eccoService.commit(namespace.getString(COMMIT_MESSAGE_KEY), namespace.getString(CONFIGURATION_KEY));
        eccoService.close();
    }

    @Override
    public void register(Subparsers commandParser, CommandRegister commandRegister) {
        Subparser commitCommandParser = commandParser.addParser(CommitCommand.COMMIT);
        commitCommandParser.setDefault(ProgramConstants.COMMAND, CommitCommand.COMMIT);
        commitCommandParser.addArgument(CommitCommand.FLAG_CONFIGURATION).required(true);
        commitCommandParser.addArgument(CommitCommand.FLAG_COMMIT_MESSAGE).setDefault("").required(false);
        commandRegister.register(CommitCommand.COMMIT, this);
    }
}
