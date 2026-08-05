package at.jku.isse.ecco.cli.command.checkout;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

public class CheckoutCommandTest {
    @Test
    public void checksOutConfiguration() {
        EccoService service = mock(EccoService.class);
        CheckoutCommand command = new CheckoutCommand(service);

        command.run(new Namespace(Map.of(
                "c", "featureA.1,featureB.1"
        )));

        verify(service).open();
        verify(service).checkout("featureA.1,featureB.1");
        verify(service).close();
    }
}
