package at.jku.isse.ecco.cli.command.commit;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CommitCommandTest {
    private EccoService service;
    private Configuration configuration;
    private StringWriter writer;

    @BeforeEach
    public void setUp() {
        service = mock(EccoService.class);
        configuration = mock(Configuration.class);
        when(configuration.getConfigurationString()).thenReturn("featureA.1");
        Commit commit = mock(Commit.class);
        when(commit.getId()).thenReturn("c0ffee");
        when(commit.getConfiguration()).thenReturn(configuration);
        when(service.commit(any(), any(String.class))).thenReturn(commit);
        writer = new StringWriter();
    }

    @Test
    public void commitsWithConfigurationAndMessage() {
        CommitCommand command = new CommitCommand(service, writer);

        command.run(new Namespace(Map.of(
                "c", "featureA.1",
                "m", "commit message"
        )));

        verify(service).open();
        verify(service).commit("commit message", "featureA.1");
        verify(service).close();
        assertEquals(List.of("Committed c0ffee as featureA.1"), writer.getLines());
    }

    @Test
    public void commitsWithoutMessage() {
        CommitCommand command = new CommitCommand(service, writer);

        Map<String, Object> args = new HashMap<>();
        args.put("c", "featureA.1");
        args.put("m", null);
        command.run(new Namespace(args));

        verify(service).open();
        verify(service).commit(null, "featureA.1");
        verify(service).close();
    }

    @Test
    public void reportsViolatedConstraints() {
        when(service.checkConstraintViolations(configuration)).thenReturn(List.of("featureA requires featureB"));
        CommitCommand command = new CommitCommand(service, writer);

        command.run(new Namespace(Map.of("c", "featureA.1")));

        assertEquals(List.of("Committed c0ffee as featureA.1", "CONSTRAINT: featureA requires featureB"), writer.getLines());
    }

    @Test
    public void closesWhenCommitFails() {
        when(service.commit(any(), any(String.class))).thenThrow(new RuntimeException("boom"));
        CommitCommand command = new CommitCommand(service, writer);

        try {
            command.run(new Namespace(Map.of("c", "featureA.1")));
        } catch (RuntimeException expected) {
        }

        verify(service).close();
        assertEquals(List.of(), writer.getLines());
    }
}
