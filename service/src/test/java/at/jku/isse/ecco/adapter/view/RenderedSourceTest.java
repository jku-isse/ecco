package at.jku.isse.ecco.adapter.view;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class RenderedSourceTest {

    private final EntityFactory ef = new SerEntityFactory();

    private Node.Op node(String name) {
        return this.ef.createNode(this.ef.createArtifact(new PluginArtifactData(name, Paths.get(name))));
    }

    /** segment texts of each line, a node-owned segment written as [text]. */
    private static List<String> show(List<List<RenderedSource.Segment>> lines, Node marked) {
        return lines.stream().map(line -> line.stream()
                .map(s -> s.selected() ? "<" + s.text() + ">" : marked != null && s.node() == marked ? "[" + s.text() + "]" : s.text())
                .collect(Collectors.joining("|"))).toList();
    }

    @Test
    public void charactersBelongToTheSmallestSpanAndLinesSplitAtNewlines() {
        Node.Op outer = node("outer"), inner = node("inner");
        outer.addChild(inner);
        String text = "def f():\n    return 1\n";
        RenderedSource source = new RenderedSource(text, List.of(
                new RenderedSource.Span(outer, 0, 21), new RenderedSource.Span(inner, 13, 21)));

        List<List<RenderedSource.Segment>> lines = source.lines(null);
        assertEquals(2, lines.size(), "the final newline ends the last line");
        assertEquals(List.of("def f():", "    |[return 1]"), show(lines, inner));
        assertSame(outer, lines.get(0).get(0).node());
        assertEquals(1, source.lineOf(inner));
    }

    @Test
    public void aNodeWithoutSpanIsSelectedThroughItsNearestAncestor() {
        Node.Op outer = node("outer"), field = node("field"), leaf = node("leaf");
        outer.addChild(field);
        field.addChild(leaf);
        RenderedSource source = new RenderedSource("ab\ncd", List.of(new RenderedSource.Span(outer, 1, 4)));

        assertEquals(List.of("a|<b>", "<c>|d"), show(source.lines(field), null));
        assertEquals(0, source.lineOf(leaf));
        assertEquals(-1, source.lineOf(node("elsewhere")));
    }

    @Test
    public void emptyTextHasOneEmptyLine() {
        assertEquals(List.of(List.of()), new RenderedSource("", List.of()).lines(null));
    }

    @Test
    public void spansMustLieInTheText() {
        assertThrows(IllegalArgumentException.class, () -> new RenderedSource("ab", List.of(new RenderedSource.Span(node("x"), 1, 3))));
    }

    @Test
    public void lineAndColumnGiveTheOffset() {
        String text = "ab\ncde\nf";
        assertEquals(0, RenderedSource.offset(text, 1, 0));
        assertEquals(4, RenderedSource.offset(text, 2, 1));
        assertEquals(7, RenderedSource.offset(text, 3, 0));
        assertEquals(text.length(), RenderedSource.offset(text, 9, 0), "past the end is clamped");
    }
}
