package at.jku.isse.ecco.cli.command;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public interface Command {

    void run(Namespace namespace);
    void register(Subparsers commandParser, CommandRegister commandRegister);
}
