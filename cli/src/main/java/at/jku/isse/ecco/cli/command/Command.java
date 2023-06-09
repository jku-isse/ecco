package at.jku.isse.ecco.cli.command;

import net.sourceforge.argparse4j.inf.Namespace;

public interface Command {

    void run(Namespace namespace);
}
