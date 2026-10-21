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

/**
 * SuggestConstraintsCommand is a thin wrapper around ConfigurationBridge.readConfigurations() +
 * ConstraintMiner, already covered in isolation elsewhere (ConstraintMinerTest,
 * ParallelMinimizationTest's sequential baseline). What's tested here is the command's own glue:
 * the open()/read/close() sequencing, the empty-repository message, and that a real commit history
 * flows through end to end. Leaving out decided suggestions: ConstraintDecisionCommandTest.
 */
public class SuggestConstraintsCommandTest {

    private Namespace defaultArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put(SuggestConstraintsCommand.MIN_WITNESS_KEY, 4);
        args.put(SuggestConstraintsCommand.CONFIDENCE_KEY, 0.9);
        args.put(SuggestConstraintsCommand.ALL_KEY, false);
        return new Namespace(args);
    }

    @Test
    @Timeout(30)
    public void printsNoSuggestionsMessageAndClosesTheServiceForARepositoryWithNoCommits() throws IOException {
        Path workDir = Files.createTempDirectory("suggest-constraints-empty");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.close();
            StringWriter writer = new StringWriter();
            SuggestConstraintsCommand command = new SuggestConstraintsCommand(service, writer);

            command.run(defaultArgs());

            assertFalse(service.isInitialized(), "closed again");
            assertEquals(List.of("No constraint suggestions (from 0 configurations)."), writer.getLines());
        }
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

    /**
     * FR-N3: constraints are mined per feature - two revisions of one feature never share a
     * configuration, and are still not suggested as excluding each other.
     */
    @Test
    public void revisionsOfOneFeatureAreNeverSuggestedAsExcludingEachOther() throws IOException {
        Path workDir = Files.createTempDirectory("suggest-constraints-revisions");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            String[] configurations = {"Core, A", "Core, A", "Core, A", "Core, A", "Core, A'", "Core, A", "Core, A", "Core, A"};
            for (int i = 0; i < configurations.length; i++) {
                Path variantDir = Files.createDirectories(workDir.resolve("variant" + i));
                Files.writeString(variantDir.resolve("core.txt"), "core\n");
                Files.writeString(variantDir.resolve("a.txt"), (i < 4 ? "a1 " : "a2 ") + i + "\n");
                service.setBaseDir(variantDir);
                service.commit("variant " + i, configurations[i]);
            }
            assertEquals(2, service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).findFirst().orElseThrow().getRevisions().size());
            service.close();

            StringWriter writer = new StringWriter();
            new SuggestConstraintsCommand(service, writer).run(defaultArgs());

            assertTrue(writer.getLines().stream().anyMatch(line -> line.startsWith("MANDATORY A")), writer.getLines().toString());
            assertTrue(writer.getLines().stream().noneMatch(line -> line.startsWith("EXCLUDES")), writer.getLines().toString());
        }
    }

    /** Twelve configurations: Core in all; B only with A; A with B but once - a near-miss. */
    private static EccoService suggestionsRepository(Path workDir) throws IOException {
        EccoService service = new EccoService();
        service.setRepositoryDir(workDir.resolve(".ecco"));
        service.init();
        String[] configurations = {"Core, A, B", "Core, A, B", "Core, A, B", "Core, A, B", "Core, A, B",
                "Core, A, B", "Core, A, B", "Core, A, B", "Core, A, B", "Core, A", "Core", "Core"};
        for (int i = 0; i < configurations.length; i++) {
            Path variantDir = Files.createDirectories(workDir.resolve("variant" + i));
            Files.writeString(variantDir.resolve("v.txt"), i + "\n");
            service.setBaseDir(variantDir);
            service.commit("variant " + i, configurations[i]);
        }
        return service;
    }

    private static List<String> suggestionLines(StringWriter writer) {
        return writer.getLines().stream().filter(l -> l.matches("(MANDATORY|REQUIRES|EXCLUDES) .*")).toList();
    }

    /** FR-N1c: hard rules first, then by number of witnesses. */
    @Test
    public void hardRulesComeFirstThenByWitnesses() throws IOException {
        Path workDir = Files.createTempDirectory("suggest-constraints-order");
        try (EccoService service = suggestionsRepository(workDir)) {
            service.close();
            StringWriter writer = new StringWriter();
            new SuggestConstraintsCommand(service, writer).run(defaultArgs());
            List<String> lines = suggestionLines(writer);
            assertTrue(lines.stream().anyMatch(l -> !l.contains("violations=")) && lines.stream().anyMatch(l -> l.contains("violations=")),
                    "both hard rules and a near-miss: " + lines);
            boolean nearMissSeen = false;
            int lastWitness = Integer.MAX_VALUE;
            for (String line : lines) {
                boolean hard = !line.contains("violations=");
                if (!hard && !nearMissSeen) {
                    nearMissSeen = true;
                    lastWitness = Integer.MAX_VALUE;
                }
                assertFalse(hard && nearMissSeen, "a hard rule after a near-miss: " + lines);
                java.util.regex.Matcher witness = java.util.regex.Pattern.compile("witness=(\\d+)").matcher(line);
                assertTrue(witness.find(), line);
                int w = Integer.parseInt(witness.group(1));
                assertTrue(w <= lastWitness, "witnesses not descending: " + lines);
                lastWitness = w;
            }
        }
    }

    /** IF-C5b: accepted and rejected suggestions are left out, unless --all lists them apart. */
    @Test
    public void decidedSuggestionsAreLeftOutUnlessAll() throws IOException {
        Path workDir = Files.createTempDirectory("suggest-constraints-all");
        try (EccoService service = suggestionsRepository(workDir)) {
            service.acceptConstraint(at.jku.isse.ecco.core.Constraint.Kind.MANDATORY, "Core", null);
            service.rejectConstraint(at.jku.isse.ecco.core.Constraint.Kind.REQUIRES, "A", "B");
            service.close();

            StringWriter pending = new StringWriter();
            new SuggestConstraintsCommand(service, pending).run(defaultArgs());
            assertTrue(suggestionLines(pending).stream().noneMatch(l -> l.startsWith("MANDATORY Core") || l.matches("REQUIRES +A -> B .*")),
                    pending.getLines().toString());

            StringWriter all = new StringWriter();
            Map<String, Object> args = new HashMap<>(defaultArgs().getAttrs());
            args.put(SuggestConstraintsCommand.ALL_KEY, true);
            new SuggestConstraintsCommand(service, all).run(new Namespace(args));
            List<String> lines = all.getLines();
            int accepted = lines.indexOf("Accepted:"), rejected = lines.indexOf("Rejected:");
            assertTrue(accepted >= 0 && rejected > accepted, lines.toString());
            assertTrue(lines.subList(accepted, rejected).stream().anyMatch(l -> l.contains("Core")), lines.toString());
            assertTrue(lines.subList(rejected, lines.size()).stream().anyMatch(l -> l.contains("A") && l.contains("B")), lines.toString());
        }
    }
}
