package at.jku.isse.ecco.cli.command.checkout;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

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
}
