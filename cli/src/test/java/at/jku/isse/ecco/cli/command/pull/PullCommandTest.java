package at.jku.isse.ecco.cli.command.pull;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

public class PullCommandTest {
    @Test
    public void pullsFromRemote() {
        EccoService service = mock(EccoService.class);
        PullCommand command = new PullCommand(service);

        command.run(new Namespace(Map.of(
                PullCommand.REMOTE_KEY, "origin",
                PullCommand.EXCLUDE_KEY, "featureA.1"
        )));

        verify(service).open();
        verify(service).pull("origin", "featureA.1");
        verify(service).close();
    }
}
