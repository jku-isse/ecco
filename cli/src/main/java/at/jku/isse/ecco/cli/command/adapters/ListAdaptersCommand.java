package at.jku.isse.ecco.cli.command.adapters;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.Collection;

public class ListAdaptersCommand implements Command {
    public final static String ADAPTERS = "adapters";
    private final EccoService eccoService;
    private final OutWriter outWriter;

    public ListAdaptersCommand(
            EccoService eccoService,
            OutWriter outWriter
    ) {
        this.eccoService = eccoService;
        this.outWriter = outWriter;
    }

    public ListAdaptersCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();
        Collection<ArtifactPlugin> plugins = eccoService.getArtifactPlugins();

        outWriter.println("");
        outWriter.println("Plugins");

        for (ArtifactPlugin plugin : plugins) {
            outWriter.printf("%s - %s - %s \n", plugin.getPluginId(), plugin.getName(), plugin.getDescription());
        }

        outWriter.println("");

        eccoService.close();
    }
}
