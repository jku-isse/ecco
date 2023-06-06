package at.jku.isse.ecco.cli.init;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class InitCommandTest {
    @Test
    public void consumesInitArgument() {
        EccoService mockedEccoService = mock(EccoService.class);
        InitCommand initCommand = new InitCommand(mockedEccoService);

        initCommand.run();

        verify(mockedEccoService).init();
    }
}
