package at.jku.isse.ecco.adapter.python.test;

import at.jku.isse.ecco.adapter.python.PythonReader;
import at.jku.isse.ecco.adapter.python.PythonRenderer;
import at.jku.isse.ecco.adapter.python.PythonWriter;
import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The association preview shows the adapter's files as PythonRenderer produces them: Python code
 * exactly as the writer writes it, JSON as the same JSON, a notebook as its cells - each with the
 * spans that color it by association. Needs `python` with libcst and py4j, like the adapter.
 */
public class PythonRenderTest {

    private static final Path READ = Paths.get("src/test/resources/data/read").toAbsolutePath();

    @BeforeAll
    static void pythonAvailable() {
        assumeTrue(PythonAvailable.check(), PythonAvailable.NEEDS);
    }

    private static Node read(String file) {
        Set<Node.Op> nodes = new PythonReader(new SerEntityFactory()).read(READ, new Path[]{READ.resolve(file)});
        assertEquals(1, nodes.size());
        return nodes.iterator().next();
    }

    private static String written(Node fileNode, String file) throws IOException {
        Path out = Files.createTempDirectory("python-render");
        // at checkout the directories are written before the files in them
        Files.createDirectories(out.resolve(file).getParent());
        new PythonWriter().write(out, Set.of(fileNode));
        return Files.readString(out.resolve(file));
    }

    private static String textOf(RenderedSource source, RenderedSource.Span span) {
        return source.getText().substring(span.start(), span.end());
    }

    @Test
    @Timeout(120)
    public void pythonCodeIsShownExactlyAsWrittenWithSpansOnItsCode() throws Exception {
        Node fileNode = read("test.py");
        RenderedSource rendered = PythonRenderer.render(fileNode);

        assertEquals(written(fileNode, "test.py"), rendered.getText());
        assertTrue(rendered.getSpans().size() > 20, "spans: " + rendered.getSpans().size());
        assertTrue(rendered.getSpans().stream().anyMatch(span -> textOf(rendered, span).equals("const = 145")),
                "a statement's span covers exactly its code");
        assertTrue(rendered.getSpans().stream().anyMatch(span -> textOf(rendered, span).startsWith("def print_hi(name):")),
                "the function's span starts at its definition");
    }

    @Test
    @Timeout(120)
    public void jsonIsShownAsTheSameJson() throws Exception {
        Node fileNode = read("ass2/ex5/working_config.json");
        RenderedSource rendered = PythonRenderer.render(fileNode);

        ObjectMapper json = new ObjectMapper();
        assertEquals(json.readTree(written(fileNode, "ass2/ex5/working_config.json")), json.readTree(rendered.getText()));
        assertTrue(rendered.getSpans().stream().anyMatch(span -> textOf(rendered, span).equals("\"device\": \"cpu\"")));
    }

    @Test
    @Timeout(180)
    public void aNotebookIsShownAsItsCells() throws Exception {
        Node fileNode = read("python.ipynb");
        RenderedSource rendered = PythonRenderer.render(fileNode);

        JsonNode cells = new ObjectMapper().readTree(written(fileNode, "python.ipynb")).get("cells");
        assertEquals(cells.size(), rendered.getText().split("# %%", -1).length - 1, "one # %% line per cell");
        for (JsonNode cell : cells) {
            StringBuilder source = new StringBuilder();
            cell.get("source").forEach(line -> source.append(line.asText()));
            String code = source.toString().strip();
            if (!code.isEmpty())
                assertTrue(rendered.getText().contains(code), "cell missing:\n" + code);
        }
    }
}
