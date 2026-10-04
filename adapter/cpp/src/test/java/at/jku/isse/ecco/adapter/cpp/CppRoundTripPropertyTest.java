package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.adapter.cpp.data.ScopeArtifactData;
import at.jku.isse.ecco.adapter.cpp.data.SourceLineArtifactData;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The C++ adapter promises byte-exact files (see CppRoundTripTest for a hand-written one). Here the
 * files are generated: namespaces, classes, structs, unions, enums, templates, functions with
 * statements, extern "C" blocks, comments, preprocessor directives and code the parser does not
 * understand, nested at random, with LF, CRLF or CR line ends, with or without a final newline,
 * in UTF-8 or Latin-1. Every one must come back byte for byte, through the adapter alone and through
 * a commit and checkout.
 */
public class CppRoundTripPropertyTest {

    @Test
    @Timeout(300)
    public void generatedFilesComeBackExactly(@TempDir Path tmp) throws IOException {
        Random random = new Random(42);
        for (int round = 0; round < 2_000; round++) {
            byte[] source = new Generator(random).file();
            byte[] result = roundTrip(tmp.resolve("round" + round), source);
            if (!Arrays.equals(source, result))
                fail("round " + round + ":\n--- source ---\n" + show(source) + "\n--- written ---\n" + show(result));
        }
    }

    @Test
    @Timeout(300)
    public void generatedFilesSurviveACommitAndACheckout(@TempDir Path tmp) throws Exception {
        Random random = new Random(7);
        Path variant = Files.createDirectories(tmp.resolve("variant"));
        Map<String, byte[]> files = new TreeMap<>();
        for (int i = 0; i < 20; i++) {
            String name = "src/file" + i + (i % 2 == 0 ? ".cpp" : ".hpp");
            byte[] source = new Generator(random).file();
            Files.createDirectories(variant.resolve(name).getParent());
            Files.write(variant.resolve(name), source);
            files.put(name, source);
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(Files.createDirectories(tmp.resolve("repo")).resolve(".ecco"));
            service.init();
            service.setBaseDir(variant);
            service.commit("generated", "A");

            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            for (Map.Entry<String, byte[]> file : files.entrySet())
                assertArrayEquals(file.getValue(), Files.readAllBytes(out.resolve(file.getKey())), file.getKey());
        }
    }

    /** FR-T1: lines carry the VEVOS presence conditions of pcs.variant.csv as proactive feature traces */
    @Test
    public void vevosPresenceConditionsBecomeFeatureTraces(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("main.cpp"), """
                #include <cstdio>

                int main() {
                    // Feature A
                    featureA();
                    return 0;
                }
                """);
        Files.writeString(tmp.resolve("pcs.variant.csv"), """
                Path;File Condition;Block Condition;Presence Condition;Line Type;start;end
                main.cpp;True;True;True;ROOT;1;7
                main.cpp;True;FEATUREA;FEATUREA;artifact;4;5
                """);

        Node file = read(tmp, "main.cpp");
        Map<String, String> conditions = new LinkedHashMap<>();
        collectConditions(file, conditions);

        assertEquals("FEATUREA", conditions.get("    // Feature A"));
        assertEquals("FEATUREA", conditions.get("    featureA();"));
        assertNull(conditions.get("int main() {"), "only the lines the condition spans");
        assertNull(conditions.get("    return 0;"));
    }

    @Test
    public void aFileWithoutTracesOrConfigurationHasNone(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("main.cpp"), "int main() {\n    return 0;\n}\n");
        Map<String, String> conditions = new LinkedHashMap<>();
        collectConditions(read(tmp, "main.cpp"), conditions);
        assertTrue(conditions.values().stream().allMatch(Objects::isNull), conditions.toString());
    }

    @Test
    public void scopesAreFoundInGeneratedFiles(@TempDir Path tmp) throws IOException {
        // not only lines: the parser's grouping is used, also for the kinds it does not fully understand
        Files.writeString(tmp.resolve("kinds.hpp"), """
                struct Point { int x; int y; };
                union Value { int i; float f; };
                template <typename T> using Vec = Box<T>;
                template <typename T> class Box { T value; };
                enum Color { RED, GREEN };
                """);
        List<String> scopes = new ArrayList<>();
        for (Node child : read(tmp, "kinds.hpp").getChildren())
            if (child.getArtifact().getData() instanceof ScopeArtifactData)
                scopes.add(child.getArtifact().getData().toString());
        assertTrue(scopes.contains("struct Point"), scopes.toString());
        assertTrue(scopes.contains("union Value"), scopes.toString());
        assertTrue(scopes.contains("enum Color"), scopes.toString());
        assertTrue(scopes.stream().anyMatch(s -> s.startsWith("template") && s.contains("Box")), scopes.toString());
    }

    private static void collectConditions(Node node, Map<String, String> conditions) {
        for (Node child : node.getChildren()) {
            if (child.getArtifact().getData() instanceof SourceLineArtifactData line) {
                FeatureTrace trace = ((Node.Op) child).getFeatureTrace();
                String condition = trace == null ? null : trace.getProactiveConditionString();
                conditions.put(line.getLine(), condition == null || condition.isEmpty() ? null : condition);
            }
            collectConditions(child, conditions);
        }
    }

    private static byte[] roundTrip(Path dir, byte[] source) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve("gen.cpp"), source);
        Node file = read(dir, "gen.cpp");
        Path out = Files.createDirectories(dir.resolve("out"));
        Path[] written = new CppWriter().write(out, Set.of(file));
        assertEquals(1, written.length);
        return Files.readAllBytes(written[0]);
    }

    private static Node read(Path dir, String name) {
        Set<Node.Op> read = new CppReader(new SerEntityFactory()).read(dir, new Path[]{Path.of(name)});
        assertEquals(1, read.size());
        return read.iterator().next();
    }

    private static String show(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1).replace("\r", "\\r");
    }

    /** A random C++ file, line by line. */
    private static final class Generator {
        private final Random random;
        private final List<String> lines = new ArrayList<>();
        private int names;

        Generator(Random random) {
            this.random = random;
        }

        byte[] file() {
            boolean guard = this.random.nextInt(3) == 0;
            if (guard) {
                this.lines.add("#ifndef GEN_H");
                this.lines.add("#define GEN_H");
            }
            for (int i = this.random.nextInt(8); i >= 0; i--)
                this.topLevel(0, "");
            if (guard)
                this.lines.add("#endif");
            if (this.lines.isEmpty())
                this.lines.add("");

            String separator = new String[]{"\n", "\n", "\r\n", "\r"}[this.random.nextInt(4)];
            StringBuilder text = new StringBuilder(String.join(separator, this.lines));
            if (this.random.nextInt(4) != 0)
                text.append(separator);
            return this.random.nextInt(5) == 0
                    ? text.toString().replace("€", "EUR").getBytes(StandardCharsets.ISO_8859_1)
                    : text.toString().getBytes(StandardCharsets.UTF_8);
        }

        private String name(String prefix) {
            return prefix + (this.names++);
        }

        private void topLevel(int depth, String indent) {
            switch (this.random.nextInt(depth < 3 ? 14 : 9)) {
                case 0 -> this.comment(indent);
                case 1 -> this.lines.add("");
                case 2 -> this.lines.add(indent + "#include " + (this.random.nextBoolean() ? "<vector>" : "\"" + this.name("h") + ".hpp\""));
                case 3 -> this.lines.add(indent + "#define " + this.name("MACRO_") + " " + this.random.nextInt(100));
                case 4 -> this.lines.add(indent + (this.random.nextBoolean() ? "static int " : "extern double ") + this.name("global") + (this.random.nextBoolean() ? " = 1;" : ";"));
                case 5 -> this.function(depth, indent, this.random.nextBoolean() ? "" : "template <typename T> ");
                case 6 -> this.enumeration(indent);
                case 7 -> this.lines.add(indent + this.junk());
                case 8 -> this.record(depth, indent);
                case 9 -> this.namespace(depth, indent);
                case 10 -> this.conditional(depth, indent);
                case 11 -> this.externC(depth, indent);
                case 12 -> this.record(depth, indent);
                default -> this.function(depth, indent, "");
            }
        }

        private void comment(String indent) {
            switch (this.random.nextInt(3)) {
                case 0 -> this.lines.add(indent + "// " + this.text());
                case 1 -> this.lines.add(indent + "/* " + this.text() + " */");
                default -> {
                    this.lines.add(indent + "/*");
                    this.lines.add(indent + " * " + this.text());
                    this.lines.add(indent + " */");
                }
            }
        }

        private String text() {
            String[] words = {"note", "café", "größe", "TODO", "a < b", "x && y", "€", "\ttab", "trailing  "};
            StringBuilder text = new StringBuilder();
            for (int i = this.random.nextInt(4); i >= 0; i--)
                text.append(i == 0 ? "" : " ").append(words[this.random.nextInt(words.length)]);
            return text.toString();
        }

        private String junk() {
            // lines the parser does not understand: they must stay as they are all the same
            return new String[]{"MACRO_CALL(x)", "}}", "int broken(", "@@ not c++ @@", "#pragma once", "#error unsupported", "template"}[this.random.nextInt(7)];
        }

        private void namespace(int depth, String indent) {
            this.lines.add(indent + "namespace " + this.name("ns") + " {");
            for (int i = this.random.nextInt(4); i >= 0; i--)
                this.topLevel(depth + 1, indent);
            this.lines.add(indent + "}");
        }

        private void externC(int depth, String indent) {
            this.lines.add(indent + "extern \"C\" {");
            this.lines.add(indent + "int " + this.name("legacy") + "(void);");
            if (this.random.nextBoolean())
                this.function(depth + 1, indent, "");
            this.lines.add(indent + "}");
        }

        private void conditional(int depth, String indent) {
            String feature = this.name("FEATURE_");
            this.lines.add(this.random.nextBoolean() ? "#ifdef " + feature : "#if defined(" + feature + ") && " + this.random.nextInt(3));
            this.topLevel(depth + 1, indent);
            if (this.random.nextBoolean()) {
                this.lines.add(this.random.nextBoolean() ? "#else" : "#elif " + this.name("OTHER_"));
                this.topLevel(depth + 1, indent);
            }
            this.lines.add("#endif" + (this.random.nextBoolean() ? " // " + feature : ""));
        }

        private void enumeration(String indent) {
            String head = indent + (this.random.nextBoolean() ? "enum class " : "enum ") + this.name("E");
            if (this.random.nextBoolean()) {
                this.lines.add(head + " { A, B, C };");
            } else {
                this.lines.add(head + " {");
                this.lines.add(indent + "    FIRST = 1,");
                this.lines.add(indent + "    SECOND");
                this.lines.add(indent + "};");
            }
        }

        private void record(int depth, String indent) {
            String kind = new String[]{"class", "struct", "union"}[this.random.nextInt(3)];
            String name = this.name("R");
            this.lines.add(indent + (this.random.nextInt(4) == 0 ? "template <typename T> " : "") + kind + " " + name
                    + (kind.equals("class") && this.random.nextBoolean() ? " : public Base" : "") + " {");
            if (kind.equals("class"))
                this.lines.add(indent + "public:");
            String inner = indent + "    ";
            for (int i = this.random.nextInt(4); i >= 0; i--) {
                switch (this.random.nextInt(5)) {
                    case 0 -> this.lines.add(inner + "int " + this.name("field") + ";");
                    case 1 -> this.comment(inner);
                    case 2 -> this.lines.add(inner + "virtual ~" + name + "() = default;");
                    case 3 -> {
                        if (depth < 3) this.function(depth + 1, inner, "");
                        else this.lines.add(inner + "void " + this.name("m") + "();");
                    }
                    default -> this.lines.add("");
                }
            }
            this.lines.add(indent + "};");
        }

        private void function(int depth, String indent, String prefix) {
            String name = this.name("f");
            String signature = prefix + (this.random.nextBoolean() ? "int " : "void ") + name + "(" + (this.random.nextBoolean() ? "int a, char** b" : "") + ")";
            if (this.random.nextInt(3) == 0) {
                this.lines.add(indent + signature);
                this.lines.add(indent + "{");
            } else {
                this.lines.add(indent + signature + " {");
            }
            for (int i = this.random.nextInt(5); i >= 0; i--)
                this.statement(indent + "    ", 0);
            this.lines.add(indent + "}");
        }

        private void statement(String indent, int depth) {
            switch (this.random.nextInt(depth < 2 ? 12 : 5)) {
                case 0 -> this.lines.add(indent + "int " + this.name("v") + " = " + this.random.nextInt(9) + ";");
                case 1 -> this.lines.add(indent + "call(" + this.random.nextInt(9) + ");");
                case 2 -> this.comment(indent);
                case 3 -> this.lines.add("");
                case 4 -> this.lines.add(indent + "return;");
                case 5 -> this.block(indent, depth, "if (a > " + this.random.nextInt(9) + ") {", this.random.nextBoolean() ? "} else {" : null);
                case 6 -> this.block(indent, depth, "for (int i = 0; i < 3; i++) {", null);
                case 7 -> this.block(indent, depth, "while (a--) {", null);
                case 8 -> {
                    this.lines.add(indent + "switch (a) {");
                    this.lines.add(indent + "case 1: call(1); break;");
                    this.lines.add(indent + "case 2:");
                    this.statement(indent + "    ", depth + 1);
                    this.lines.add(indent + "    break;");
                    this.lines.add(indent + "default:");
                    this.lines.add(indent + "    break;");
                    this.lines.add(indent + "}");
                }
                case 9 -> {
                    this.lines.add(indent + "auto " + this.name("l") + " = [&](int x) {");
                    this.lines.add(indent + "    return x + a;");
                    this.lines.add(indent + "};");
                }
                case 10 -> this.block(indent, depth, "try {", "} catch (const std::exception& e) {");
                default -> {
                    this.lines.add("#ifdef " + this.name("FEATURE_"));
                    this.statement(indent, depth + 1);
                    this.lines.add("#endif");
                }
            }
        }

        private void block(String indent, int depth, String open, String middle) {
            this.lines.add(indent + open);
            this.statement(indent + "    ", depth + 1);
            if (middle != null) {
                this.lines.add(indent + middle);
                this.statement(indent + "    ", depth + 1);
            }
            this.lines.add(indent + "}");
        }
    }
}
