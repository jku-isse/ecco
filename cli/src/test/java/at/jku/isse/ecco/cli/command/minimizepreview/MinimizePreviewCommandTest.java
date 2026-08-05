package at.jku.isse.ecco.cli.command.minimizepreview;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.repository.Repository;
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
 * MinimizePreviewCommand glues ConfigurationBridge, ConstraintMiner, AcceptedConstraints and
 * ParallelMinimization together (each already covered on its own elsewhere, e.g.
 * ParallelMinimizationTest). What's untested is the command's own logic: the header line's
 * accepted/hard-constraint counting, the "id" filter that narrows to a single association, and that
 * a real accepted MANDATORY constraint actually reaches the per-association minimized output.
 */
public class MinimizePreviewCommandTest {

    private Namespace argsWithId(String id) {
        Map<String, Object> args = new HashMap<>();
        args.put(MinimizePreviewCommand.ID_KEY, id);
        args.put(MinimizePreviewCommand.MIN_WITNESS_KEY, 4);
        args.put(MinimizePreviewCommand.CONFIDENCE_KEY, 0.9);
        return new Namespace(args);
    }

    @Test
    public void printsOnlyTheHeaderForAnEmptyRepository() {
        EccoService service = mock(EccoService.class);
        Repository repository = mock(Repository.class);
        when(service.getCommits()).thenReturn(List.of());
        when(service.getRepository()).thenReturn(repository);
        doReturn(List.of()).when(repository).getConstraints();
        doReturn(List.of()).when(repository).getAssociations();
        StringWriter writer = new StringWriter();
        MinimizePreviewCommand command = new MinimizePreviewCommand(service, writer);

        command.run(argsWithId(null));

        verify(service).open();
        verify(service).close();
        assertEquals(List.of(
                "Feature model: 0 accepted hard constraint(s) compiled (of 0 accepted total; near-misses and stale/unreproducible ones are excluded).",
                ""
        ), writer.getLines());
    }

    /**
     * Four commits all selecting Core give MANDATORY Core exactly minWitness(4) witnesses (mirrors
     * SuggestConstraintsCommandTest's real-data setup), and acceptConstraint() makes it an accepted
     * hard constraint the header should count. Filtering by "id" to a single association's own id
     * checks the id filter narrows output to exactly that one association, not all of them.
     */
    @Test
    @Timeout(30)
    public void realAcceptedConstraintIsAppliedAndIdFilterNarrowsToOneAssociation() throws IOException {
        Path workDir = Files.createTempDirectory("minimize-preview-real");
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
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "Core", null);

            List<Association> associations = new java.util.ArrayList<>(service.getRepository().getAssociations());
            assertTrue(associations.size() > 1, "sanity check: need more than one association for the id filter to prove anything");
            String targetId = associations.get(0).getId();

            service.close();

            StringWriter writer = new StringWriter();
            MinimizePreviewCommand command = new MinimizePreviewCommand(service, writer);

            command.run(argsWithId(targetId));

            assertEquals("Feature model: 1 accepted hard constraint(s) compiled (of 1 accepted total; near-misses and stale/unreproducible ones are excluded).",
                    writer.getLines().get(0));
            assertEquals("", writer.getLines().get(1));

            long associationHeaderLines = writer.getLines().stream().filter(line -> line.startsWith("[")).count();
            assertEquals(1, associationHeaderLines, "the id filter should narrow output to exactly one association's block");
            assertTrue(writer.getLines().get(2).startsWith("[" + targetId + "] "));
        }
    }
}
