package at.jku.isse.ecco.cli.command.dependencygraph;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class DependencyGraphCommand implements Command {
    public final static String DG = "dg";
    public final static String DEPENDENCY_GRAPH_ALIAS = "dependencyGraph";
    private final EccoService eccoService;
    private final OutWriter writer;

    public DependencyGraphCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public DependencyGraphCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();

        writer.println(new DependencyGraph(eccoService.getRepository().getAssociations()).getGMLString());

        eccoService.close();
    }
}
