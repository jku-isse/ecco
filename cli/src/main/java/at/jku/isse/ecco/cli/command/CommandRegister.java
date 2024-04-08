package at.jku.isse.ecco.cli.command;

import net.sourceforge.argparse4j.inf.Namespace;

import java.util.HashMap;
import java.util.Map;

public class CommandRegister {
    private final Map<String, Command> commandMap = new HashMap<>();

    public void register(String commandString, Command command) {
        commandMap.put(commandString, command);
    }

    public void run(String commandString, Namespace namespace) {
        commandMap.get(commandString).run(namespace);
    }
}
