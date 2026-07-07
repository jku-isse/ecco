package at.jku.isse.ecco.cli.command.property;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class SetCommandTest {
    @Test
    public void setsBaseDir() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        SetCommand command = new SetCommand(service, stringWriter);

        command.run(new Namespace(Map.of(
                SetCommand.PROPERTY_KEY, "basedir",
                SetCommand.VALUE_KEY, "/newbase"
        )));

        verify(service).open();
        verify(service).setBaseDir(Paths.get("/newbase"));
        verify(service).close();
        assertEquals(1, stringWriter.getLines().size());
        assertEquals("baseDir=" + Path.of("/newbase"), stringWriter.getLines().get(0));
    }

    @Test
    public void reportsUnknownProperty() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        SetCommand command = new SetCommand(service, stringWriter);

        command.run(new Namespace(Map.of(
                SetCommand.PROPERTY_KEY, "unknown",
                SetCommand.VALUE_KEY, "value"
        )));

        assertEquals(1, stringWriter.getLines().size());
        assertEquals("ERROR: No property named \"unknown\".", stringWriter.getLines().get(0));
    }
}
