package at.jku.isse.ecco.adapter.lilypond.parce.graalvm;

import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import org.graalvm.polyglot.HostAccess;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LilypondService {
    private static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private final LilypondNode<ParceToken> root;
    private LilypondNode<ParceToken> current;
    private int depth;
    private int maxDepth;
    private int nNodes;

    public LilypondService() {
        root = new LilypondNode<>("ROOT", null);
        reset();
    }

    public void reset() {
        current = root;
        current.setNext(null);
        depth = 0;
        maxDepth = 0;
        nNodes = 0;
    }

    LilypondNode<ParceToken> getRoot() {
        return root.getNext();
    }

    public int getNodesCount() {
        return nNodes;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    @HostAccess.Export
    public void pushContext(String name) {
        current = current.append(name, null, depth++);
        LOGGER.log(Level.FINER, "opened context ({0}, depth={1})", new Object[]{ name, depth });
        maxDepth = Math.max(depth, maxDepth);
        nNodes++;
    }

    @HostAccess.Export
    public void addToken(int pos, String text, String action) {
        ParceToken t = new ParceToken(pos, text, action);
        current = current.append(t.getAction(), t, depth);
        nNodes++;
        LOGGER.log(Level.FINER, "added token ([{0}] \"{1}\", depth={2})",
                new Object[] { t.getAction(), t.getText(), depth });
    }

    @HostAccess.Export
    public void addWhitespace(int pos, String text) {
        if (null == text) { return; }

        String[] lines = text.split("\\n", -1);
        if (lines.length > 1) {
            ParceToken t = new ParceToken(pos,
                    "\n".repeat(lines.length - 1).concat(lines[lines.length - 1]),
                    LilypondReader.PARSER_ACTION_LINEBREAK);
            current = current.append(t.getAction(), t, depth);
            nNodes++;
            LOGGER.log(Level.FINER, "added whitespace token ([{0}] \"{1}\", depth={2})",
                    new Object[] { t.getAction(), t.getText(), depth });
        }
    }

    @HostAccess.Export
    public void popContext(int levels) {
        if (levels >= 0) { return; }

        depth += levels;
        LOGGER.log(Level.FINER, "closed context ({0}, depth={1})", new Object[]{ levels, depth });
    }

}
