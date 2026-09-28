package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proactive feature traces from a VEVOS presence-condition file (pcs.variant.csv) combined with
 * ECCO's retroactive tracing. VEVOS names features ("LOG"), but configurations were evaluated with
 * only their revisions set, so such a condition never held: the traced lines - and, boosted, their
 * whole association - were left out of every checkout.
 * <p>
 * Two variants are committed: CORE, and CORE with LOG and VAL. Retroactively, the log and the
 * validation line cannot be told apart (they always came together); the proactive traces tell them.
 */
public class CProactiveTraceTest {

    private static final String CORE = "int main() {\n    int x = 1;\n    return 0;\n}\n";
    private static final String FULL = "int main() {\n    int x = 1;\n    log(x);\n    if (x < 0) return 1;\n    return 0;\n}\n";
    private static final String HEADER = "Path;File Condition;Block Condition;Presence Condition;Line Type;start;end\n";

    private static String checkout(String presenceConditions, String configuration) throws Exception {
        Path work = Files.createTempDirectory("c-proactive");
        Path core = Files.createDirectories(work.resolve("core"));
        Path full = Files.createDirectories(work.resolve("full"));
        Files.writeString(core.resolve("main.c"), CORE);
        Files.writeString(full.resolve("main.c"), FULL);
        Files.writeString(full.resolve("pcs.variant.csv"), HEADER + "main.c;True;True;True;ROOT;1;6\n" + presenceConditions);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(core);
            service.commit("core", "CORE");
            service.setBaseDir(full);
            service.commit("full", "CORE, LOG, VAL");
            Path out = Files.createDirectories(work.resolve("out"));
            service.setBaseDir(out);
            service.checkout(configuration);
            return Files.readString(out.resolve("main.c"));
        }
    }

    private static final String BOTH_TRACED = "main.c;True;LOG;LOG;artifact;3;3\nmain.c;True;VAL;VAL;artifact;4;4\n";

    @Test
    @Timeout(120)
    public void tracedLinesAreSelectedByTheirFeatures() throws Exception {
        assertEquals("int main() {\n    int x = 1;\n    log(x);\n    return 0;\n}\n", checkout(BOTH_TRACED, "CORE, LOG"));
        assertEquals("int main() {\n    int x = 1;\n    if (x < 0) return 1;\n    return 0;\n}\n", checkout(BOTH_TRACED, "CORE, VAL"));
        assertEquals(FULL, checkout(BOTH_TRACED, "CORE, LOG, VAL"));
    }

    /** One traced line is boosted: its condition applies to its whole association. */
    @Test
    @Timeout(120)
    public void aSingleTraceIsBoostedToItsAssociation() throws Exception {
        String logOnly = "main.c;True;LOG;LOG;artifact;3;3\n";
        assertEquals(FULL, checkout(logOnly, "CORE, LOG, VAL"));
        assertEquals(FULL, checkout(logOnly, "CORE, LOG"));
        assertEquals(CORE, checkout(logOnly, "CORE, VAL"));
    }
}
