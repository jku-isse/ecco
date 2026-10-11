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

        String[] lines = text.split("\\n", -1);
        if (lines.length > 1) {
            lastText = null;
            LilypondToken t = new LilypondToken(pos,
                    "\n".repeat(lines.length - 1).concat(lines[lines.length - 1]),
                    LilypondReader.PARSER_ACTION_LINEBREAK);
            current = current.append(t.getAction(), t, depth);
            nNodes++;
            LOGGER.log(Level.FINER, "added whitespace token ([{0}] \"{1}\", depth={2})",
                    new Object[] { t.getAction(), t.getText(), depth });
        }
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
