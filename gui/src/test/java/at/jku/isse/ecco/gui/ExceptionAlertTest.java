package at.jku.isse.ecco.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An error dialog says why, not only what failed: the messages of the causes, without repeats. */
public class ExceptionAlertTest {

    @Test
    public void theCausesAreShownWithoutRepeats() {
        Exception e = new RuntimeException("Error when retrieving repository.",
                new RuntimeException("Could not read the repository /x/.ecco: Operation timed out. Keep it outside synchronized folders.",
                        new IOException("Operation timed out")));
        assertEquals("Error when retrieving repository.\nCould not read the repository /x/.ecco: Operation timed out. Keep it outside synchronized folders.",
                ExceptionAlert.describe(e));
        assertEquals("java.lang.IllegalStateException", ExceptionAlert.describe(new IllegalStateException()));
        assertEquals("only", ExceptionAlert.describe(new RuntimeException("only", new RuntimeException("only"))));
    }
}
