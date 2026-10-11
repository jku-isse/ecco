package at.jku.isse.ecco.cli.command.commit;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    public void reportsViolatedConstraintsCheckedBeforeCommitting() {
        Configuration toCommit = mock(Configuration.class);
        when(service.parseConfigurationString("featureA.1")).thenReturn(toCommit);
        when(service.checkConstraintViolations(toCommit)).thenReturn(List.of("featureA requires featureB"));
        CommitCommand command = new CommitCommand(service, writer);

        command.run(new Namespace(Map.of("c", "featureA.1")));

        assertEquals(List.of("Committed c0ffee as featureA.1", "CONSTRAINT: featureA requires featureB"), writer.getLines());
        // afterwards the committed configuration is the exception to the rule, which then no longer counts
        InOrder order = inOrder(service);
        order.verify(service).checkConstraintViolations(toCommit);
        order.verify(service).commit(any(), any(String.class));
        verify(service, never()).checkConstraintViolations(configuration);
    }

    @Test
    public void reportsViolatedConstraintsOfARealRepository(@TempDir Path tmp) throws Exception {
        // as Main does: no JavaFX viewers on the command line
        System.setProperty("ecco.headless", "true");
        Path repo = tmp.resolve(".ecco");
        try (EccoService real = new EccoService()) {
            real.setRepositoryDir(repo);
            real.init();
            // an accepted constraint only counts while the commits still witness it (at least 4)
            for (int i = 0; i < 4; i++)
                commitFile(real, tmp.resolve("seed" + i), "seed " + i, "BASE, A");
            real.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "A", null);
        }
        EccoService real = new EccoService();
        real.setRepositoryDir(repo);
        Path work = Files.createDirectories(tmp.resolve("work"));
        Files.writeString(work.resolve("f.txt"), "work\n");
        real.setBaseDir(work);

        new CommitCommand(real, writer).run(new Namespace(Map.of("c", "BASE", "m", "without A")));

        assertEquals(2, writer.getLines().size(), String.valueOf(writer.getLines()));
        assertTrue(writer.getLines().get(1).startsWith("CONSTRAINT: MANDATORY A violated"), writer.getLines().get(1));
    }

    private static void commitFile(EccoService service, Path dir, String content, String configuration) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), content + "\n");
        service.setBaseDir(dir);
        service.commit("", configuration);
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
