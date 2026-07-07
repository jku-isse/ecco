package at.jku.isse.ecco.cli.command.remotes;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.List;

public class RemotesCommand implements Command {
    public final static String REMOTES = "remotes";
    public static final String FLAG_ADD = "--add";
    public static final String ADD_KEY = "add";
    public static final String FLAG_REMOVE = "--remove";
    public static final String REMOVE_KEY = "remove";
    public static final String FLAG_TYPE = "--type";
    public static final String TYPE_KEY = "type";
    public static final String NAME_KEY = "name";
    private final EccoService eccoService;
    private final OutWriter writer;

    public RemotesCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public RemotesCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        List<String> add = namespace.getList(ADD_KEY);
        String remove = namespace.getString(REMOVE_KEY);
        String type = namespace.getString(TYPE_KEY);
        String name = namespace.getString(NAME_KEY);

        eccoService.open();

        if (add != null) {
            Remote remote = eccoService.addRemote(add.get(0), add.get(1), Remote.Type.valueOf(type.toUpperCase()));
            writer.println("SUCCESS: added remote " + remote.getName() + ": " + remote.getAddress() + " [" + remote.getType() + "]");
        } else if (remove != null) {
            eccoService.removeRemote(remove);
            writer.println("SUCCESS: removed remote " + remove);
        } else if (name != null) {
            printRemote(name);
        } else {
            for (Remote remote : eccoService.getRemotes()) {
                writer.println(remote.getName() + ": " + remote.getAddress() + " [" + remote.getType() + "]");
            }
        }

        eccoService.close();
    }

    private void printRemote(String name) {
        Remote remote = eccoService.getRemote(name);
        if (remote != null) {
            writer.println(remote.getName() + ": " + remote.getAddress() + " [" + remote.getType() + "]");
        } else {
            writer.println("Remote " + name + " does not exist.");
        }
    }
}
