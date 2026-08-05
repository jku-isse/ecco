package at.jku.isse.ecco.cli.command.commit;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class CommitCommandTest {
    @Test
    public void commitsWithConfigurationAndMessage() {
        EccoService service = mock(EccoService.class);
        CommitCommand command = new CommitCommand(service);

        command.run(new Namespace(Map.of(
                "c", "featureA.1",
                "m", "commit message"
        )));

        verify(service).open();
        verify(service).commit("commit message", "featureA.1");
        verify(service).close();
    }

    @Test
    public void commitsWithoutMessage() {
        EccoService service = mock(EccoService.class);
        CommitCommand command = new CommitCommand(service);

        Map<String, Object> args = new HashMap<>();
        args.put("c", "featureA.1");
        args.put("m", null);
        command.run(new Namespace(args));

        verify(service).open();
        verify(service).commit(null, "featureA.1");
        verify(service).close();
    }
}
