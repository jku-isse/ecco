package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The association preview shows a file as TypeScriptWriter.render() produces it: exactly the text
 * written at checkout, with a span for every node that lies within its parent's span.
 */
public class TypeScriptRenderTest {

    @Test
    @Timeout(120)
    public void renderGivesTheWrittenTextAndNestedSpansForEveryNode() throws Exception {
        String source = "import { a } from './a';\n"
                + "enum Color { Red, Green }\n"
                + "let x: number = 1, y = 2;\n"
                + "class C {\n  f(n: number) {\n    if (n > 0) {\n      return n;\n    } else {\n      return -n;\n    }\n  }\n}\n"
                + "for (let i = 0; i < 3; i++) {\n  console.log(i);\n}\n"
                + "const g = (s: string) => s.length;\n";
        Path base = Files.createTempDirectory("typescript-render");
        Files.writeString(base.resolve("main.ts"), source);
        EntityFactory entityFactory = new SerEntityFactory();
        Node fileNode = new TypeScriptReader(entityFactory).read(base, new Path[]{Paths.get("main.ts")}).iterator().next();

        RenderedSource rendered = TypeScriptWriter.render(fileNode);

        Path out = Files.createTempDirectory("typescript-render-out");
        new TypeScriptWriter().write(out, Set.of(fileNode));
        assertEquals(Files.readString(out.resolve("main.ts")), rendered.getText(), "the preview must show what is written");

        Map<Node, RenderedSource.Span> spans = new IdentityHashMap<>(); // nodes with equal content are equal
        for (RenderedSource.Span span : rendered.getSpans())
            assertNull(spans.put(span.node(), span), "one span per node");
        List<Node> stack = new java.util.ArrayList<>(fileNode.getChildren());
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            RenderedSource.Span span = spans.get(node);
            assertNotNull(span, "no span for " + node);
            for (Node child : node.getChildren()) {
                RenderedSource.Span childSpan = spans.get(child);
                assertNotNull(childSpan);
                assertTrue(span.start() <= childSpan.start() && childSpan.end() <= span.end(), child + " lies outside its parent");
                stack.add(child);
            }
        }
        assertTrue(spans.size() > 10, "the file was parsed into many nodes: " + spans.size());
    }
}
