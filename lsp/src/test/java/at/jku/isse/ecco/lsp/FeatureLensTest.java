package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each line of a checked-out file is traced to the features of the association it comes from - at
 * the line it is on in the file, not where the committing variant had it - and an edit, saved or
 * not, is new until it is committed.
 */
@Timeout(120)
public class FeatureLensTest {

    static final Map<String, String> TEXT = Map.of(
            "BASE, A", "base1\na1\nbase2\n",
            "BASE, B", "base1\nbase2\nb1\n",
            "BASE, A, B", "base1\na1\nbase2\nb1\n");

    static String java(boolean a, boolean b) {
        return "package p;\n\npublic class Foo {\n\n"
                + "    int base() {\n        return 0;\n    }\n\n"
                + (a ? "    int a() {\n        int x = 1;\n        return x;\n    }\n\n" : "")
                + (b ? "    int b() {\n        return 2;\n    }\n\n" : "")
                + "    int last() {\n        return 3;\n    }\n}\n";
    }

    /** Commits BASE, A / BASE, B / BASE, A, B and checks out [configuration] into [tmp]/work. */
    static Path checkout(Path tmp, String configuration) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            int i = 0;
            for (String config : List.of("BASE, A", "BASE, B", "BASE, A, B")) {
                Path dir = Files.createDirectories(tmp.resolve("v" + i++));
                Files.writeString(dir.resolve("notes.txt"), TEXT.get(config));
                Files.writeString(dir.resolve("Foo.java"), java(config.contains(", A"), config.endsWith(", B")));
                service.setBaseDir(dir);
                service.commit("v" + i, config);
            }
            service.setBaseDir(work);
            service.checkout(configuration);
        }
        return work;
    }

    @Test
    public void eachLineOfATextFileHasTheConditionOfItsAssociation(@TempDir Path tmp) throws Exception {
        Path work = checkout(tmp, "BASE, A, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(Path.of("notes.txt"), Files.readString(work.resolve("notes.txt")));
            assertNull(trace.problem());
            // .config names the revisions checked out: "A.3f39114, B.553ba6a, BASE.2815d71"
            assertTrue(trace.configuration().matches("A\\.\\w+, B\\.\\w+, BASE\\.\\w+"), trace.configuration());
            assertEquals("BASE", trace.at(1).condition());
            assertEquals("A", trace.at(2).condition());
            assertEquals("BASE", trace.at(3).condition());
            // committed as line 3 of BASE, B; line 4 here
            assertEquals("B", trace.at(4).condition());
        }
    }

    @Test
    public void javaMembersAndStatementsAreTracedWhereTheyAreWritten(@TempDir Path tmp) throws Exception {
        Path work = checkout(tmp, "BASE, A, B");
        String content = Files.readString(work.resolve("Foo.java"));
        List<String> lines = content.lines().toList();
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(work.resolve("Foo.java"), content);
            assertNull(trace.problem());
            assertEquals("A", trace.at(lineOf(lines, "int x = 1;")).condition());
            assertEquals("A", trace.at(lineOf(lines, "int a()")).condition());
            assertEquals("B", trace.at(lineOf(lines, "return 2;")).condition());
            assertEquals("BASE", trace.at(lineOf(lines, "int last()")).condition());
            assertEquals("BASE", trace.at(lineOf(lines, "return 0;")).condition());
        }
    }

    @Test
    public void anUnsavedEditIsNotCommitted(@TempDir Path tmp) throws Exception {
        Path work = checkout(tmp, "BASE, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(Path.of("notes.txt"), "base1\nnew line\nbase2\nb1\n");
            assertEquals("BASE", trace.at(1).condition());
            assertFalse(trace.at(2).committed());
            assertEquals("BASE", trace.at(3).condition());
            assertEquals("B", trace.at(4).condition());

            String java = Files.readString(work.resolve("Foo.java")).replace("return 2;", "return 22;");
            FeatureLens.FileTrace javaTrace = lens.trace(Path.of("Foo.java"), java);
            List<String> lines = java.lines().toList();
            assertFalse(javaTrace.at(lineOf(lines, "return 22;")).committed());
            assertEquals("B", javaTrace.at(lineOf(lines, "int b()")).condition());
        }
    }

    @Test
    public void javaThatDoesNotParseGivesAProblem(@TempDir Path tmp) throws Exception {
        Path work = checkout(tmp, "BASE, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(Path.of("Foo.java"), "package p;\n\npublic class Foo {\n    int b( {\n}\n");
            assertNotNull(trace.problem());
            assertTrue(trace.spans().isEmpty());
        }
    }

    @Test
    public void aFileOutsideTheCheckoutOrWithoutCheckoutGivesAProblem(@TempDir Path tmp) throws Exception {
        Path work = checkout(tmp, "BASE, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            assertTrue(lens.trace(Path.of("other.txt"), "x\n").problem().contains("not part of the checkout"));
            Files.delete(work.resolve(".config"));
            assertTrue(lens.trace(Path.of("notes.txt"), "base1\n").problem().contains("no checkout"));
        }
    }

    @Test
    public void aCommitFromAnotherServiceIsSeen(@TempDir Path tmp) throws Exception {
        Path work = checkout(tmp, "BASE, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            assertFalse(lens.trace(Path.of("notes.txt"), "base1\nbase2\nb1\nc1\n").at(4).committed());
            try (EccoService other = new EccoService()) {
                Path dir = Files.createDirectories(tmp.resolve("vc"));
                Files.writeString(dir.resolve("notes.txt"), "base1\nbase2\nb1\nc1\n");
                other.setRepositoryDir(work.resolve(".ecco"));
                other.setBaseDir(dir);
                other.open();
                other.commit("c", "BASE, B, C");
            }
            Files.writeString(work.resolve(".config"), "BASE, B, C");
            assertEquals("C", lens.trace(Path.of("notes.txt"), "base1\nbase2\nb1\nc1\n").at(4).condition());
        }
    }

    /** Commits base, a (BASE, A) and base, b (BASE, B) and checks out BASE, A, B into [tmp]/work: the order of a and b is undecided. */
    static Path orderCheckout(Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            Path dir = Files.createDirectories(tmp.resolve("v"));
            service.setBaseDir(dir);
            Files.writeString(dir.resolve("notes.txt"), "base\na\n");
            service.commit("a", "BASE, A");
            Files.writeString(dir.resolve("notes.txt"), "base\nb\n");
            service.commit("b", "BASE, B");
            service.setBaseDir(work);
            service.checkout("BASE, A, B");
        }
        return work;
    }

    /** a and b were each added after base by one variant: their order is undecided, at lines 2-3 of the file. */
    @Test
    public void anOrderWarningIsAtTheUndecidedLines(@TempDir Path tmp) throws Exception {
        Path work = orderCheckout(tmp);
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(Path.of("notes.txt"), Files.readString(work.resolve("notes.txt")));
            assertEquals(1, trace.orderWarnings().size(), trace.orderWarnings().toString());
            FeatureLens.OrderWarning warning = trace.orderWarnings().getFirst();
            assertEquals(2, warning.startLine());
            assertEquals(3, warning.endLine());
            assertTrue(warning.message().startsWith("Order undecided between variants: lines 2-3"), warning.message());
        }
    }

    /** The Markdown adapter records no lines: the file's own condition, and why there is no more. */
    @Test
    public void aFileWithoutLinePositionsHasItsFilesCondition(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            Path dir = Files.createDirectories(tmp.resolve("v"));
            service.setBaseDir(dir);
            Files.writeString(dir.resolve("notes.txt"), "base\n");
            service.commit("base", "BASE");
            Files.writeString(dir.resolve("README.md"), "# Title\n\nText.\n");
            service.commit("a", "BASE, A");
            service.setBaseDir(work);
            service.checkout("BASE, A");
        }
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(Path.of("README.md"), Files.readString(work.resolve("README.md")));
            assertEquals("A", trace.fileCondition());
            assertNull(trace.at(1));
            assertTrue(trace.problem().contains("records no line positions"), trace.problem());
        }
    }

    /** The hover's details: the description of the line's feature and the commits it came with; none for code not committed. */
    @Test
    public void aLineTellsItsFeaturesDescriptionAndCommits(@TempDir Path tmp) throws Exception {
        Path work = checkout(tmp, "BASE, A, B");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.open();
            String a = service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).findFirst().orElseThrow().getId();
            service.setFeatureDescription(a, "Adds the a lines.");
        }
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(Path.of("notes.txt"), "base1\na1\nbase2\nb1\nnew\n");
            String details = trace.at(2).details();
            assertNotNull(details);
            assertTrue(details.contains("**A**: Adds the a lines."), details);
            // a1 came with v1 (BASE, A) and v3 (BASE, A, B), messages "v1" and "v3"
            assertTrue(details.contains("2 commits, the last") && details.contains("'v3'"), details);
            assertTrue(trace.at(1).details().contains("3 commits"), trace.at(1).details());
            assertNull(trace.at(5).details());
        }
    }

    static int lineOf(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++)
            if (lines.get(i).contains(text))
                return i + 1;
        throw new AssertionError(text + " not in " + lines);
    }
}
