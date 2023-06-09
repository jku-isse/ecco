package at.jku.isse.ecco.cli.command.init;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class InitCommandTest {
    @Test
    public void consumesInitArgument() {
        EccoService mockedEccoService = mock(EccoService.class);
        InitCommand initCommand = new InitCommand(mockedEccoService);

        initCommand.run(new Namespace(Map.of()));

        verify(mockedEccoService).init();
    }
}
