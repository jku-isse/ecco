package at.jku.isse.ecco.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The CLI exited with 0 even when the arguments were invalid or the command failed.
 */
public class MainExitCodeTest {

    @Test
    public void invalidArgumentsExitWith2() {
        assertEquals(2, Main.run(new String[]{"no-such-command"}));
    }

    @Test
    public void aFailingCommandExitsWith1() {
        // forking into the working directory needs a free location and a valid origin - an
        // unparsable remote address is rejected by the command itself
        assertEquals(1, Main.run(new String[]{"fork", "\u0000invalid"}));
    }

    /** IF-C2: commit and checkout require a configuration - without -c the arguments are invalid. */
    @Test
    public void commitAndCheckoutWithoutAConfigurationExitWith2() {
        assertEquals(2, Main.run(new String[]{"commit"}));
        assertEquals(2, Main.run(new String[]{"checkout"}));
    }

    /**
     * IF-C3b: a failed command's error goes to stderr with its cause chain; the stack trace only
     * with -Decco.debug=true.
     */
    @Test
    public void aFailureIsReportedOnStderrWithItsCausesAndATraceOnlyInDebugMode() {
        java.io.PrintStream err = System.err;
        String debug = System.getProperty("ecco.debug");
        try {
            for (boolean debugMode : new boolean[]{false, true}) {
                if (debugMode) System.setProperty("ecco.debug", "true");
                else System.clearProperty("ecco.debug");
                java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
                System.setErr(new java.io.PrintStream(captured, true));
                assertEquals(1, Main.run(new String[]{"fork", "\u0000invalid"}));
                String output = captured.toString();
                org.junit.jupiter.api.Assertions.assertTrue(output.startsWith("ERROR: "), output);
                org.junit.jupiter.api.Assertions.assertEquals(debugMode, output.contains("\tat "), output);
            }
        } finally {
            System.setErr(err);
            if (debug == null) System.clearProperty("ecco.debug");
            else System.setProperty("ecco.debug", debug);
        }
    }
}
