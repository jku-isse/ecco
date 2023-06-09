package at.jku.isse.ecco.cli.command.adapters;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ListAdaptersCommandTest {
    @Test
    public void printsArtefactPlugins() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        ListAdaptersCommand command = new ListAdaptersCommand(service, stringWriter);

        doReturn(List.of(
            new TestAdapter(1),
            new TestAdapter(2),
            new TestAdapter(3),
            new TestAdapter(4)
        )).when(service).getArtifactPlugins();

        command.run(new Namespace(Map.of()));

        verify(service).getArtifactPlugins();

        assertEquals(7, stringWriter.getLines().size());
        assertEquals("", stringWriter.getLines().get(0));
        assertEquals("Plugins", stringWriter.getLines().get(1));
        assertEquals("plugin 1 - name 1 - description 1 \n", stringWriter.getLines().get(2));
        assertEquals("plugin 2 - name 2 - description 2 \n", stringWriter.getLines().get(3));
        assertEquals("plugin 3 - name 3 - description 3 \n", stringWriter.getLines().get(4));
        assertEquals("plugin 4 - name 4 - description 4 \n", stringWriter.getLines().get(5));
        assertEquals("", stringWriter.getLines().get(6));
    }
}
