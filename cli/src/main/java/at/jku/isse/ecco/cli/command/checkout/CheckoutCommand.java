package at.jku.isse.ecco.cli.command.checkout;

import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class CheckoutCommand implements Command {
    public static final String CHECKOUT = "checkout";
    public static final String FLAG_CONFIGURATION = "-c";
    private static final String CONFIGURATION_KEY = "c";
    private final EccoService eccoService;

    public CheckoutCommand(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();
        eccoService.checkout(namespace.getString(CONFIGURATION_KEY));
        eccoService.close();
    }

    @Override
    public void register(Subparsers commandParser, CommandRegister commandRegister) {
        Subparser commitCommandParser = commandParser.addParser(CheckoutCommand.CHECKOUT);
        commitCommandParser.setDefault(ProgramConstants.COMMAND, CheckoutCommand.CHECKOUT);
        commitCommandParser.addArgument(CheckoutCommand.FLAG_CONFIGURATION).required(true);
        commandRegister.register(CheckoutCommand.CHECKOUT, new CheckoutCommand(eccoService));
    }
}
