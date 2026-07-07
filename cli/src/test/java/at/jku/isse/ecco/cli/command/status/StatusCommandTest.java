package at.jku.isse.ecco.cli.command.status;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class StatusCommandTest {
    @Test
    public void printsStatus() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        StatusCommand command = new StatusCommand(service, stringWriter);

        when(service.getRepositoryDir()).thenReturn(Path.of("/repo/.ecco"));
        when(service.getBaseDir()).thenReturn(Path.of("/repo"));
        when(service.getConfigStringFromFile(Path.of("/repo"))).thenReturn("featureA.1");

        command.run(new Namespace(Map.of()));

        verify(service).open();
        verify(service).close();

        assertEquals(3, stringWriter.getLines().size());
        assertTrue(stringWriter.getLines().get(0).contains("/repo/.ecco"));
        assertTrue(stringWriter.getLines().get(1).contains("/repo"));
        assertTrue(stringWriter.getLines().get(2).contains("featureA.1"));
    }
}
