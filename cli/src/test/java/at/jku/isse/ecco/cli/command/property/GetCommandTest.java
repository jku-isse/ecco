package at.jku.isse.ecco.cli.command.property;

import at.jku.isse.ecco.cli.CliException;
import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class GetCommandTest {
    @Test
    public void getsBaseDir() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        GetCommand command = new GetCommand(service, stringWriter);

        when(service.getBaseDir()).thenReturn(Path.of("/repo"));

        command.run(new Namespace(Map.of(GetCommand.PROPERTY_KEY, "basedir")));

        verify(service).open();
        verify(service).close();
        assertEquals(1, stringWriter.getLines().size());
        assertEquals("baseDir=/repo", stringWriter.getLines().get(0));
    }

    @Test
    public void reportsUnknownProperty() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        GetCommand command = new GetCommand(service, stringWriter);

        CliException exception = assertThrows(CliException.class, () -> command.run(new Namespace(Map.of(GetCommand.PROPERTY_KEY, "unknown"))));

        assertEquals("No property named \"unknown\".", exception.getMessage());
        assertEquals(0, stringWriter.getLines().size());
    }
}
