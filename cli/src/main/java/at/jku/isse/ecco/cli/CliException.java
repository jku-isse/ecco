package at.jku.isse.ecco.cli;

/**
 * A command failed for a reason the user can fix (unknown property, invalid address, ...). Main
 * prints it as an ERROR line and exits with a non-zero status - commands used to print the error
 * and then return normally, so the CLI exited with 0 and scripts couldn't tell.
 */
public class CliException extends RuntimeException {

    public CliException(String message) {
        super(message);
    }
}
