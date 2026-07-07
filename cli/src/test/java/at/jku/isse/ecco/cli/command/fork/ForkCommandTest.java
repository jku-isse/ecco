package at.jku.isse.ecco.cli.command.fork;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ForkCommandTest {
    @Test
    public void forksFromRemoteHostAndPort() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        ForkCommand command = new ForkCommand(service, stringWriter);

        command.run(new Namespace(Map.of(
                ForkCommand.REMOTE_KEY, "localhost:1234",
                ForkCommand.EXCLUDE_KEY, ""
        )));

        verify(service).fork("localhost", 1234, "");
        verify(service).close();
    }

    @Test
    public void forksFromLocalPath() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        ForkCommand command = new ForkCommand(service, stringWriter);

        command.run(new Namespace(Map.of(
                ForkCommand.REMOTE_KEY, "/some/other/repo",
                ForkCommand.EXCLUDE_KEY, ""
        )));

        verify(service).fork(Path.of("/some/other/repo"), "");
        verify(service).close();
    }
}
