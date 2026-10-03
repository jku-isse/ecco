package at.jku.isse.ecco.cli.command.checkout;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class CheckoutCommandTest {
    private EccoService service;
    private Checkout checkout;
    private StringWriter writer;

    @BeforeEach
    public void setUp() {
        service = mock(EccoService.class);
        Configuration configuration = mock(Configuration.class);
        when(configuration.getConfigurationString()).thenReturn("featureA.1, featureB.1");
        checkout = new Checkout();
        checkout.setConfiguration(configuration);
        when(service.checkout("featureA.1,featureB.1")).thenReturn(checkout);
        writer = new StringWriter();
    }

    @Test
    public void checksOutConfiguration() {
        CheckoutCommand command = new CheckoutCommand(service, writer);

        command.run(new Namespace(Map.of(
                "c", "featureA.1,featureB.1"
        )));

        verify(service).open();
        verify(service).checkout("featureA.1,featureB.1");
        verify(service).close();
        assertEquals(List.of("Checked out featureA.1, featureB.1"), writer.getLines());
    }

    @Test
    public void summarizesWarnings() {
        checkout.getConstraintWarnings().add("featureA excludes featureB");
        checkout.getConstraintWarnings().add("featureB requires featureC");
        CheckoutCommand command = new CheckoutCommand(service, writer);

        command.run(new Namespace(Map.of("c", "featureA.1,featureB.1")));

        assertEquals(List.of("Checked out featureA.1, featureB.1",
                "Warnings: 2 CONSTRAINT -- details in .warnings"), writer.getLines());
    }
}
