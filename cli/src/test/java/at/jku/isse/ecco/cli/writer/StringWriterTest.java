package at.jku.isse.ecco.cli.writer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StringWriterTest {
    @Test()
    public void containsPrintedStrings() {
        StringWriter stringWriter = new StringWriter();

        ((OutWriter) stringWriter).println("line 1");
        ((OutWriter) stringWriter).println("line 2");
        ((OutWriter) stringWriter).println("line 3");

        assertNotNull(stringWriter.getLines());
        assertFalse(stringWriter.getLines().isEmpty());
        assertEquals(3, stringWriter.getLines().size());
        assertEquals("line 1", stringWriter.getLines().get(0));
        assertEquals("line 2", stringWriter.getLines().get(1));
        assertEquals("line 3", stringWriter.getLines().get(2));
    }
}
