package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Commits examples/proactive_traces_variants (see its README): LOG was only ever committed together
 * with VERBOSE, which has no code of its own, so the history cannot tell which of the two the log code
 * belongs to; a proactive trace (pcs.variant.csv) marks the log function as LOG, but not the call to
 * it. Boosting (the default main-tree strategy) gives the whole association the trace's condition:
 * "BASE, VERBOSE" checks out the base program. Without it, the call stays and the function goes - a
 * program that does not compile.
 */
public class ProactiveTracesExampleTest {

    private static final Path EXAMPLE = findRepoRoot().resolve("examples").resolve("proactive_traces_variants");

    @Test
    @Timeout(60)
    public void boostingGivesTheUnannotatedCallTheTraceOfItsFunction() throws Exception {
        try (EccoService service = committed("BOOST")) {
            assertEquals(variant("V1_base"), checkout(service, "BASE.1"));
            assertEquals(variant("V2_base_log_verbose"), checkout(service, "BASE.1, LOG.1, VERBOSE.1"));
            assertEquals(variant("V1_base"), checkout(service, "BASE.1, VERBOSE.1"), "VERBOSE has no code: the base program");
            assertEquals(variant("V2_base_log_verbose"), checkout(service, "BASE.1, LOG.1"));
        }
    }

    @Test
    @Timeout(60)
    public void withoutBoostingTheCallStaysAndTheFunctionGoes() throws Exception {
        try (EccoService service = committed("NO-BOOST")) {
            String mainC = checkout(service, "BASE.1, VERBOSE.1");
            assertTrue(mainC.contains("log_message(\"starting\");"), mainC);
            assertFalse(mainC.contains("static void log_message"), mainC);
        }
    }

    private static EccoService committed(String mainTreeStrategy) throws Exception {
        EccoService service = new EccoService();
        service.getProperties().setProperty(EccoService.ECCO_PROPERTIES_MAIN_TREE_BUILDING_STRATEGY, mainTreeStrategy);
        service.setRepositoryDir(Files.createTempDirectory("proactive-traces").resolve(".ecco"));
        service.init();
        for (String variant : new String[]{"V1_base", "V2_base_log_verbose"}) {
            service.setBaseDir(EXAMPLE.resolve(variant));
            service.commit(variant);
        }
        return service;
    }

    /** main.c of the checkout of [configuration], without blank lines (the C adapter does not keep them). */
    private static String checkout(EccoService service, String configuration) throws Exception {
        Path dir = Files.createTempDirectory("proactive-traces-checkout");
        service.setBaseDir(dir);
        service.checkout(configuration);
        return withoutBlankLines(Files.readString(dir.resolve("main.c")));
    }

    private static String variant(String name) throws Exception {
        return withoutBlankLines(Files.readString(EXAMPLE.resolve(name).resolve("main.c")));
    }

    private static String withoutBlankLines(String content) {
        return content.lines().filter(line -> !line.isBlank()).reduce("", (a, b) -> a + b + "\n");
    }

    private static Path findRepoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
            if (Files.exists(dir.resolve("settings.gradle")))
                return dir;
        throw new IllegalStateException("Could not locate repository root (no settings.gradle found in any ancestor directory).");
    }
}
