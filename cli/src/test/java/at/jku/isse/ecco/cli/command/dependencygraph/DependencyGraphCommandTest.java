package at.jku.isse.ecco.cli.command.dependencygraph;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class DependencyGraphCommandTest {
    @Test
    public void printsDependencyGraph() {
        EccoService service = mock(EccoService.class);
        Repository repository = mock(Repository.class);
        StringWriter stringWriter = new StringWriter();
        DependencyGraphCommand command = new DependencyGraphCommand(service, stringWriter);

        when(service.getRepository()).thenReturn(repository);
        doReturn(List.of()).when(repository).getAssociations();

        command.run(new Namespace(Map.of()));

        verify(service).open();
        verify(service).close();
        assertEquals(1, stringWriter.getLines().size());
        assertTrue(stringWriter.getLines().get(0).contains("graph"));
    }
}
