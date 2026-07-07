package at.jku.isse.ecco.cli.command.push;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

public class PushCommandTest {
    @Test
    public void pushesToRemote() {
        EccoService service = mock(EccoService.class);
        PushCommand command = new PushCommand(service);

        command.run(new Namespace(Map.of(
                PushCommand.REMOTE_KEY, "origin",
                PushCommand.EXCLUDE_KEY, "featureA.1"
        )));

        verify(service).open();
        verify(service).push("origin", "featureA.1");
        verify(service).close();
    }
}
