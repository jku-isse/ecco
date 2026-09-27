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
}
