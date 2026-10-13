package at.jku.isse.ecco.adapter.lilypond.parser.py4j;

import at.jku.isse.ecco.adapter.lilypond.LilypondFormatter;
import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.parser.LilypondToken;

import java.util.logging.Level;
import java.util.logging.Logger;

public class EntryPoint {
    private static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private final LilypondNode<LilypondToken> root;
    private LilypondNode<LilypondToken> current;
    private int depth;
    private int maxDepth;
    private int nNodes;
    // the token read last, where it ended (in UTF-16 units, like the positions the script sends), for
    // LilypondToken#isAttached; null after a line break
    private String lastText;
    private int lastEnd;
    // the whitespace read since the last token, on the current line
    private String space;

    public EntryPoint() {
        root = new LilypondNode<>("ROOT", null);
        reset();
    }

    public void reset() {
        current = root;
        current.setNext(null);
        depth = 0;
        maxDepth = 0;
        nNodes = 0;
        lastText = null;
        lastEnd = -1;
        space = "";
    }

    public int getNodesCount() {
        return nNodes;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void pushContext(String name) {
        current = current.append(name, null, depth++);
        LOGGER.log(Level.FINER, "opened context ({0}, depth={1})", new Object[]{ name, depth });
        maxDepth = Math.max(depth, maxDepth);
        nNodes++;
    }

    public void addToken(int pos, String text, String action) {
        LilypondToken t = new LilypondToken(pos, text, action);
        t.setAttached(lastText != null && pos == lastEnd && LilypondFormatter.glue(lastText, text));
        t.setSpaceBefore(space);
        space = "";
        lastText = text;
        lastEnd = pos + text.length();
        current = current.append(t.getAction(), t, depth);
        nNodes++;
        LOGGER.log(Level.FINER, "added token ([{0}] \"{1}\", depth={2})",
                new Object[] { t.getAction(), t.getText(), depth });
    }

    /** A note, rest or chord end with what it means however it is spelled (see LilypondToken#getMeaning). */
    public void addToken(int pos, String text, String action, String meaning) {
        addToken(pos, text, action);
        current.getData().setMeaning(meaning);
    }

    public void addWhitespace(int pos, String text) {
        if (null == text) { return; }

        int lineEnd = text.indexOf('\n');
        if (lineEnd < 0) {
            space = space.concat(text);
            return;
        }
        // the break's text: from its first line end on, with the blank lines and the next line's
        // indentation as written; what stood before it on its line is its space before
        lastText = null;
        LilypondToken t = new LilypondToken(pos, text.substring(lineEnd),
                LilypondReader.PARSER_ACTION_LINEBREAK);
        t.setSpaceBefore(space.concat(text.substring(0, lineEnd)));
        space = "";
        current = current.append(t.getAction(), t, depth);
        nNodes++;
        LOGGER.log(Level.FINER, "added whitespace token ([{0}] \"{1}\", depth={2})",
                new Object[] { t.getAction(), t.getText(), depth });
    }

    public void popContext(int levels) {
        if (levels >= 0) { return; }

        depth += levels;
        LOGGER.log(Level.FINER, "closed context ({0}, depth={1})", new Object[]{ levels, depth });
    }

    LilypondNode<LilypondToken> getRoot() {
        return root.getNext();
    }
}
