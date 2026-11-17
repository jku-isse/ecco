package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;

import java.util.*;

/**
 * The lines (1-based, inclusive) the nodes of a tree just read are on, as its adapter says them:
 * <ul>
 * <li>{@code LINE_START}/{@code LINE_END} properties - text, Java, LilyPond;</li>
 * <li>a {@link Location} property - the C and C++ adapters' lines;</li>
 * <li>else the adapter's rendering of the file ({@code SourceRenderer}) - TypeScript, Python, Go -
 * where its text is the file's: the part each node produced, without the whitespace around it.</li>
 * </ul>
 */
final class NodeLines {

    static final String LINE_START = "LINE_START";
    static final String LINE_END = "LINE_END";
    static final String LOCATION = "Location";

    private final Map<Node, int[]> rendered;

    private NodeLines(Map<Node, int[]> rendered) {
        this.rendered = rendered;
    }

    /** From the nodes' properties only. */
    static NodeLines ofProperties() {
        return new NodeLines(Map.of());
    }

    /** From [source], a rendering of the tree; null if its text is not [content] (line breaks aside). */
    static NodeLines ofRendering(RenderedSource source, String content) {
        String text = source.getText();
        if (!normalized(text).equals(normalized(content)))
            return null;
        List<Integer> lineStarts = new ArrayList<>(List.of(0));
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\n')
                lineStarts.add(i + 1);
        Map<Node, int[]> lines = new IdentityHashMap<>();
        for (RenderedSource.Span span : source.getSpans()) {
            int start = span.start(), end = span.end();
            while (start < end && Character.isWhitespace(text.charAt(start)))
                start++;
            while (end > start && Character.isWhitespace(text.charAt(end - 1)))
                end--;
            if (start < end)
                lines.put(span.node(), new int[]{lineOf(lineStarts, start), lineOf(lineStarts, end - 1)});
        }
        return new NodeLines(lines);
    }

    /** Whether some node below [node] knows its lines from its properties. */
    static boolean anyProperties(Node node) {
        for (Node child : node.getChildren())
            if (fromProperties(child) != null || anyProperties(child))
                return true;
        return false;
    }

    /** The lines of [node], or null. */
    int[] of(Node node) {
        int[] lines = fromProperties(node);
        return lines != null ? lines : this.rendered.get(node);
    }

    /** The first line of [node] or of its first child that has one; -1 if none has. */
    int first(Node node) {
        int[] lines = this.of(node);
        if (lines != null)
            return lines[0];
        for (Node child : node.getChildren()) {
            int first = this.first(child);
            if (first > 0)
                return first;
        }
        return -1;
    }

    /** The last line of [node] and its descendants; -1 if none has one. */
    int last(Node node) {
        int[] lines = this.of(node);
        int last = lines == null ? -1 : lines[1];
        for (Node child : node.getChildren())
            last = Math.max(last, this.last(child));
        return last;
    }

    private static int[] fromProperties(Node node) {
        Optional<Object> start = node.getProperty(LINE_START);
        if (start.isPresent() && start.get() instanceof Number s) {
            int end = node.getProperty(LINE_END).filter(Number.class::isInstance).map(e -> ((Number) e).intValue()).orElse(s.intValue());
            return new int[]{s.intValue(), Math.max(s.intValue(), end)};
        }
        Optional<Object> location = node.getProperty(LOCATION);
        if (location.isPresent() && location.get() instanceof Location l && l.getStartLine() > 0)
            return new int[]{l.getStartLine(), Math.max(l.getStartLine(), l.getEndLine())};
        return null;
    }

    private static String normalized(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** The 1-based line of a character offset. */
    private static int lineOf(List<Integer> lineStarts, int offset) {
        int index = Collections.binarySearch(lineStarts, offset);
        return (index >= 0 ? index : -index - 2) + 1;
    }
}
