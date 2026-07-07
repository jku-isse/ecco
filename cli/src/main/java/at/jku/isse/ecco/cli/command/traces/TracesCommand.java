package at.jku.isse.ecco.cli.command.traces;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.util.Trees;
import net.sourceforge.argparse4j.inf.Namespace;

public class TracesCommand implements Command {
    public final static String TRACES = "traces";
    public static final String ID_KEY = "id";
    private final EccoService eccoService;
    private final OutWriter writer;

    public TracesCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public TracesCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        String id = namespace.getString(ID_KEY);

        eccoService.open();

        for (Association association : eccoService.getRepository().getAssociations()) {
            if (id == null || association.getId().equals(id)) {
                writer.println("[" + association.getId() + "] " + association.computeCondition().getModuleRevisionConditionString());
                if (id != null) {
                    Trees.print(association.getRootNode());
                }
            }
        }

        eccoService.close();
    }
}
