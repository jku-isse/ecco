package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.AdapterPreferences;
import at.jku.isse.ecco.adapter.golang.GoPlugin;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.python.parse.py4j.PY4JParser;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Each adapter's lines, as the language server finds them: the C and C++ adapters' Location, the
 * TypeScript, Python and Go adapters' rendering of the file read. A function feature A added is A,
 * the code around it BASE, at the lines it is on in the checked-out file.
 */
@Timeout(180)
public class AdapterLinesTest {

    @Test
    public void c(@TempDir Path tmp) throws Exception {
        String base = "#include <stdio.h>\n\nint base(void) {\n    return 1;\n}\n\nint last(void) {\n    return 3;\n}\n";
        assertTraced(tmp, "main.c", "at.jku.isse.ecco.adapter.c.CPlugin", base,
                base.replace("int last", "int featureA(void) {\n    return 2;\n}\n\nint last"));
    }

    @Test
    public void cpp(@TempDir Path tmp) throws Exception {
        String base = "#include <cstdio>\n\nclass Base {\npublic:\n    int base() {\n        return 1;\n    }\n};\n\nint last() {\n    return 3;\n}\n";
        assertTraced(tmp, "main.cpp", null, base,
                base.replace("int last", "int featureA() {\n    return 2;\n}\n\nint last"));
    }

    @Test
    public void typeScript(@TempDir Path tmp) throws Exception {
        String base = "function base(): number {\n    return 1;\n}\n\nfunction last(): number {\n    return 3;\n}\n";
        assertTraced(tmp, "main.ts", null, base,
                base.replace("function last", "function featureA(): number {\n    return 2;\n}\n\nfunction last"));
    }

    /** A Go token is its text at its row and column: the feature's code is added after the rest, which a line inserted above moves. */
    @Test
    public void go(@TempDir Path tmp) throws Exception {
        String base = "package main\n\nfunc base() int {\n\treturn 1\n}\n\nfunc last() int {\n\treturn 3\n}\n";
        // the Go adapter is off by default (a user preference): on for this test, as GoVariantsRoundTripTest does
        Set<String> disabled = AdapterPreferences.getDisabledPluginIds();
        Set<String> withGo = new HashSet<>(disabled);
        withGo.remove(GoPlugin.class.getName());
        AdapterPreferences.setDisabledPluginIds(withGo);
        try {
            assertTraced(tmp, "main.go", GoPlugin.class.getName(), base, base + "\nfunc featureA() int {\n\treturn 2\n}\n");
        } finally {
            AdapterPreferences.setDisabledPluginIds(disabled);
        }
    }

    @Test
    public void python(@TempDir Path tmp) throws Exception {
        try {
            PY4JParser.python();
        } catch (EccoException e) {
            assumeTrue(false, "needs a python with the libcst and py4j modules");
        }
        String base = "def base():\n    return 1\n\n\ndef last():\n    return 3\n";
        assertTraced(tmp, "main.py", null, base,
                base.replace("def last", "def feature_a():\n    return 2\n\n\ndef last"));
    }

    /**
     * Commits [base] as BASE and [withA] as BASE, A, checks out BASE, A, and traces the file: "return 2"
     * is A, "return 1" and "return 3" BASE. [plugin], if given, is the adapter that must have read it.
     */
    private static void assertTraced(Path tmp, String file, String plugin, String base, String withA) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            Path dir = Files.createDirectories(tmp.resolve("v"));
            service.setBaseDir(dir);
            Files.writeString(dir.resolve(file), base);
            service.commit("base", "BASE");
            Files.writeString(dir.resolve(file), withA);
            service.commit("a", "BASE, A");
            String read = pluginOf(service, file);
            assertNotEquals("at.jku.isse.ecco.adapter.text.TextPlugin", read, "read as text: the adapter under test did not read " + file);
            assertNotEquals("at.jku.isse.ecco.adapter.file.FilePlugin", read, "read as a file: the adapter under test did not read " + file);
            if (plugin != null)
                assertEquals(plugin, read);
            service.setBaseDir(work);
            service.checkout("BASE, A");
        }
        String content = Files.readString(work.resolve(file));
        List<String> lines = content.lines().toList();
        try (FeatureLens lens = new FeatureLens(work)) {
            FeatureLens.FileTrace trace = lens.trace(Path.of(file), content);
            assertNull(trace.problem(), trace.problem());
            assertEquals("A", conditionAt(trace, lines, "return 2"), content);
            assertEquals("BASE", conditionAt(trace, lines, "return 1"), content);
            assertEquals("BASE", conditionAt(trace, lines, "return 3"), content);
        }
    }

    private static String conditionAt(FeatureLens.FileTrace trace, List<String> lines, String text) {
        FeatureLens.Span span = trace.at(FeatureLensTest.lineOf(lines, text));
        return span == null ? "(nothing at " + text + ")" : span.condition();
    }

    /** The adapter the repository read [file] with. */
    private static String pluginOf(EccoService service, String file) {
        for (Association association : service.getRepository().getAssociations()) {
            String plugin = pluginOf(association.getRootNode(), file);
            if (plugin != null)
                return plugin;
        }
        return null;
    }

    private static String pluginOf(Node node, String file) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data && data.getPath().toString().equals(file))
            return data.getPluginId();
        for (Node child : node.getChildren()) {
            String plugin = pluginOf(child, file);
            if (plugin != null)
                return plugin;
        }
        return null;
    }
}
