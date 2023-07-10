package at.jku.isse.ecco.cli.command.features;

import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparsers;

import java.util.Collection;

public class ListFeaturesCommand implements Command {
    public final static String FEATURES = "features";
    private final EccoService eccoService;
    private final OutWriter writer;

    public ListFeaturesCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public ListFeaturesCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();

        Collection<? extends Feature> features = this.eccoService.getRepository().getFeatures();

        for (Feature feature : features) {
            writer.println(feature.toString());
        }

        eccoService.close();
    }

    @Override
    public void register(Subparsers commandParser, CommandRegister commandRegister) {
        commandParser.addParser(FEATURES).setDefault(ProgramConstants.COMMAND, FEATURES);
        commandRegister.register(FEATURES, this);
    }
}
