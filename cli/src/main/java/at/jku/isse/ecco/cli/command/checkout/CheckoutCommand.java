package at.jku.isse.ecco.cli.command.checkout;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.ArrayList;
import java.util.List;

public class CheckoutCommand implements Command {
    public static final String CHECKOUT = "checkout";
    public static final String FLAG_CONFIGURATION = "-c";
    private static final String CONFIGURATION_KEY = "c";
    public static final String FLAG_MINIMIZED = "--minimized";
    private static final String MINIMIZED_KEY = "minimized";
    private final EccoService eccoService;
    private final OutWriter writer;

    public CheckoutCommand(EccoService eccoService, OutWriter writer) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public CheckoutCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();
        try {
            eccoService.setMinimizedConditionsInCheckout(Boolean.TRUE.equals(namespace.getBoolean(MINIMIZED_KEY)));
            Checkout checkout = eccoService.checkout(namespace.getString(CONFIGURATION_KEY));
            writer.println("Checked out " + checkout.getConfiguration().getConfigurationString());
            String summary = warningSummary(checkout);
            if (!summary.isEmpty())
                writer.println("Warnings: " + summary + " -- details in .warnings");
        } finally {
            eccoService.close();
        }
    }

    /**
     * Counts per kind, in the order and with the names of the lines in {@code .warnings}; "" if there are none.
     */
    static String warningSummary(Checkout checkout) {
        List<String> parts = new ArrayList<>();
        add(parts, checkout.getMissing().size(), "MISSING");
        add(parts, checkout.getSurplusModules().size(), "SURPLUS");
        add(parts, checkout.getOrderWarnings().size(), "ORDER");
        add(parts, checkout.getUnresolvedAssociations().size(), "UNRESOLVED");
        add(parts, checkout.getConstraintWarnings().size(), "CONSTRAINT");
        add(parts, checkout.getRejectedTraces().size(), "TRACE");
        return String.join(", ", parts);
    }

    private static void add(List<String> parts, int count, String kind) {
        if (count > 0)
            parts.add(count + " " + kind);
    }
}
