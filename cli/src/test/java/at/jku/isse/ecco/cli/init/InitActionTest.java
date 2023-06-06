package at.jku.isse.ecco.cli.init;

import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class InitActionTest {
    @Test
    public void consumesInitArgument() {
        EccoService mockedEccoService = mock(EccoService.class);
        ArgumentParser parser = ArgumentParsers.newFor(ProgramConstants.ECCO).build();
        parser.addArgument(ProgramConstants.INIT).action(new InitAction(mockedEccoService));

        try {
            parser.parseArgs(new String[]{ProgramConstants.INIT});
        } catch (ArgumentParserException e) {
            fail(e.getMessage());
        }

        verify(mockedEccoService).init();
    }
}
