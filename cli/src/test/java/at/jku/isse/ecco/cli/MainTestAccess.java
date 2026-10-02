package at.jku.isse.ecco.cli;

import java.nio.file.Path;

/** Lets command tests in other packages run the CLI as if started in a directory. */
public final class MainTestAccess {

    private MainTestAccess() {
    }

    public static int run(String[] args, Path startDir) {
        return Main.run(args, startDir);
    }
}
