package at.jku.isse.ecco.cli.command.fetch;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

public class FetchCommandTest {
    @Test
    public void fetchesFromRemote() {
        EccoService service = mock(EccoService.class);
        FetchCommand command = new FetchCommand(service);

        command.run(new Namespace(Map.of(FetchCommand.REMOTE_KEY, "origin")));

        verify(service).open();
        verify(service).fetch("origin");
        verify(service).close();
    }
}
