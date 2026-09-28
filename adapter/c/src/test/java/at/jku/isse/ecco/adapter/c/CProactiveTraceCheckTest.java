package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.featuretrace.RejectedTrace;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proactive feature traces are checked against the commit history before they are used (see
 * ProactiveTraceCheck): a trace false for a commit containing its line (too narrow), or true for a
 * commit without it (too broad), is provably wrong - it is not used (nor boosted) and is reported.
 * <p>
 * Three variants: CORE; CORE, LOG with a log line; CORE, VAL with a validation line. The log
 * line's trace comes with the LOG variant.
 */
public class CProactiveTraceCheckTest {

    private static final String HEADER = "Path;File Condition;Block Condition;Presence Condition;Line Type;start;end\n";
    private static final String CORE = "int main() {\n    int x = 1;\n    return 0;\n}\n";
    private static final String LOG = "int main() {\n    int x = 1;\n    log(x);\n    return 0;\n}\n";
    private static final String VAL = "int main() {\n    int x = 1;\n    if (x < 0) return 1;\n    return 0;\n}\n";

    private record Result(Checkout checkout, String file, String warnings) {
    }

    private static Result checkout(String logLineTrace, String configuration) throws Exception {
        Path work = Files.createTempDirectory("c-trace-check");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            commit(service, work.resolve("core"), CORE, null, "CORE");
            commit(service, work.resolve("log"), LOG, HEADER + "main.c;True;True;True;ROOT;1;5\nmain.c;True;" + logLineTrace + ";" + logLineTrace + ";artifact;3;3\n", "CORE, LOG");
            commit(service, work.resolve("val"), VAL, HEADER + "main.c;True;True;True;ROOT;1;5\nmain.c;True;VAL;VAL;artifact;3;3\n", "CORE, VAL");
            Path out = Files.createDirectories(work.resolve("out"));
            service.setBaseDir(out);
            Checkout checkout = service.checkout(configuration);
            return new Result(checkout, Files.readString(out.resolve("main.c")), Files.readString(out.resolve(EccoService.WARNINGS_FILE_NAME)));
        }
    }

    private static void commit(EccoService service, Path dir, String content, String presenceConditions, String configuration) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("main.c"), content);
        if (presenceConditions != null)
            Files.writeString(dir.resolve("pcs.variant.csv"), presenceConditions);
        service.setBaseDir(dir);
        service.commit(configuration, configuration);
    }

    @Test
    @Timeout(120)
    public void aTraceFalseWhereItsLineWasIsRejected() throws Exception {
        Result result = checkout("VAL", "CORE, LOG");

        // the line is where the history puts it, not where the wrong trace would
        assertEquals(LOG, result.file());
        List<RejectedTrace> rejected = result.checkout().getRejectedTraces();
        assertEquals(1, rejected.size(), rejected.toString());
        RejectedTrace trace = rejected.get(0);
        assertEquals(RejectedTrace.Direction.TOO_NARROW, trace.direction());
        assertEquals("VAL", trace.condition());
        assertEquals("main.c:3", trace.location());
        assertEquals("LOG", trace.suggestion());
        assertTrue(result.warnings().contains("TRACE: main.c:3: trace \"VAL\" is too narrow"), result.warnings());
    }

    @Test
    @Timeout(120)
    public void aTraceTrueWhereItsLineWasNotIsRejected() throws Exception {
        Result result = checkout("LOG | VAL", "CORE, VAL");

        assertEquals(VAL, result.file());
        List<RejectedTrace> rejected = result.checkout().getRejectedTraces();
        assertEquals(1, rejected.size(), rejected.toString());
        assertEquals(RejectedTrace.Direction.TOO_BROAD, rejected.get(0).direction());
        assertEquals("LOG", rejected.get(0).suggestion());
    }

    @Test
    @Timeout(120)
    public void aTraceThatFitsTheHistoryIsUsed() throws Exception {
        Result result = checkout("LOG", "CORE, LOG");

        assertEquals(LOG, result.file());
        assertTrue(result.checkout().getRejectedTraces().isEmpty(), result.checkout().getRejectedTraces().toString());
        assertFalse(result.warnings().contains("TRACE:"), result.warnings());
    }
}
