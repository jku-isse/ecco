package at.jku.isse.ecco.cli.command.suggestconstraints;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SuggestConstraintsCommand is a thin wrapper around ConfigurationBridge.readConfigurations() +
 * ConstraintMiner, already covered in isolation elsewhere (ConstraintMinerTest,
 * ParallelMinimizationTest's sequential baseline). What's untested is the command's own glue: the
 * open()/read/close() sequencing, the empty-repository message, and that a real commit history
 * flows through end to end without throwing.
 */
public class SuggestConstraintsCommandTest {

    private Namespace defaultArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put(SuggestConstraintsCommand.MIN_WITNESS_KEY, 4);
        args.put(SuggestConstraintsCommand.CONFIDENCE_KEY, 0.9);
        return new Namespace(args);
    }

    @Test
    public void printsNoSuggestionsMessageAndClosesTheServiceForARepositoryWithNoCommits() {
        EccoService service = mock(EccoService.class);
        when(service.getCommits()).thenReturn(List.of());
        StringWriter writer = new StringWriter();
        SuggestConstraintsCommand command = new SuggestConstraintsCommand(service, writer);

        command.run(defaultArgs());

        verify(service).open();
        verify(service).close();
        assertEquals(List.of("No constraint suggestions (from 0 configurations)."), writer.getLines());
    }

    /**
     * Four commits that all include "Core" give MANDATORY Core exactly minWitness(4) witnesses, so
     * this is a deterministic real-data smoke test, not just "doesn't throw": it exercises the whole
     * open() -> readConfigurations() -> close() -> mine() -> print path against a real repository and
     * checks the actual suggestion shows up.
     */
    @Test
    @Timeout(30)
    public void realCommitHistoryProducesTheExpectedMandatorySuggestion() throws IOException {
        Path workDir = Files.createTempDirectory("suggest-constraints-real");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            for (int i = 0; i < 4; i++) {
                Path variantDir = workDir.resolve("variant" + i);
                Files.createDirectories(variantDir);
                Files.writeString(variantDir.resolve("core.txt"), "core\n");
                if (i % 2 == 0) {
                    Files.writeString(variantDir.resolve("extra.txt"), "extra " + i + "\n");
                }
                service.setBaseDir(variantDir);
                service.commit("variant " + i, i % 2 == 0 ? "Core, Extra" : "Core");
            }
            service.close();

            StringWriter writer = new StringWriter();
            SuggestConstraintsCommand command = new SuggestConstraintsCommand(service, writer);

            command.run(defaultArgs());

            assertEquals("Constraint suggestions from 4 configurations:", writer.getLines().get(0));
            assertTrue(writer.getLines().stream().anyMatch(line -> line.startsWith("MANDATORY Core")),
                    "expected a MANDATORY Core suggestion since every one of the 4 commits selected Core; got: " + writer.getLines());
        }
    }
}
