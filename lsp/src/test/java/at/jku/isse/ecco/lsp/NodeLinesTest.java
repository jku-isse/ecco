package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lines from a rendering: a node's span, without the whitespace around it, at the lines it covers -
 * and none where the rendering is not the file's text, whose lines would be another's.
 */
public class NodeLinesTest {

    private static Node node() {
        return (Node) Proxy.newProxyInstance(Node.class.getClassLoader(), new Class<?>[]{Node.class}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getChildren" -> List.of();
                    case "getProperty" -> java.util.Optional.empty();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @Test
    public void aSpansLinesAreThoseOfItsTextWithoutTheWhitespaceAroundIt() {
        Node a = node(), b = node();
        String text = "x\n\n  int a;\n  int b() {\n  }\n";
        RenderedSource source = new RenderedSource(text, List.of(
                new RenderedSource.Span(a, 1, text.indexOf("  int b")), // "\n\n  int a;\n"
                new RenderedSource.Span(b, text.indexOf("  int b"), text.length())));
        NodeLines lines = NodeLines.ofRendering(source, text.replace("\n", "\r\n"));
        assertNotNull(lines);
        assertArrayEquals(new int[]{3, 3}, lines.of(a));
        assertArrayEquals(new int[]{4, 5}, lines.of(b));
    }

    @Test
    public void aRenderingThatIsNotTheFilesTextGivesNoLines() {
        Node a = node();
        RenderedSource source = new RenderedSource("int a;\n", List.of(new RenderedSource.Span(a, 0, 6)));
        assertNull(NodeLines.ofRendering(source, "int  a;\n"));
    }
}
