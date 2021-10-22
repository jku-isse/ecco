package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import java.util.HashMap;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LilyEccoTransformer {
    private static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private final static String DEF_VARIABLE = "Name.Variable.Definition";
    private final static String DEF_LYRIC = "Keyword.Lyric";
    private final static String DEF_LYRICLIST = "LilyPond.lyriclist";
    private final static String DEF_MUSICLIST = "LilyPond.musiclist";
    private final static String DEF_LIST = "LilyPond.list";
    private final static String DEF_BRACKET_START = "Delimiter.Bracket.Start";
    private final static String DEF_BRACKET_END = "Delimiter.Bracket.End";
    private final static String DEF_PITCH = "Text.Music.Pitch";
    private final static String DEF_REST = "Text.Music.Rest";
    private final static HashMap<String, Set<String>> noteAcceptables = new HashMap<>();

    static {
        initNoteAcceptables();
    }

    private static int depthOffset;
    private static int cntInput;
    private static int cntOutput;

    public static void transform(LilypondNode<ParceToken> head) {
        if (null == head) {
            LOGGER.warning("first node is null");
            return;
        }

        depthOffset = 0;
        cntInput = 0;
        cntOutput = 0;
        String acceptanceTrace = "";

        long tm = System.nanoTime();
        LOGGER.finer("\n\n****** START TRANSFORMATION ******\n");
        Stack<Set<String>> waitFor = new Stack<>();
        Set<String> accepts = null;
        int waitForDepth = -1;

        LilypondNode<ParceToken> n = head;
        while (n != null) {
            cntInput++;

            if (n.getData() != null) {
                if (n.getData().getAction().equals(DEF_VARIABLE) && isTransformableVariable(n)) {
                    n = moveUntilLevel(n, n.getLevel());

                    depthOffset++;
                    n.changeLevelBy(depthOffset); // Delimiter.Operator.Assignment

                    n = moveNext(n);
                    if (n.getName().equals(DEF_LIST)) {
                        waitForDepth = n.getLevel();
                        n.changeLevelBy(depthOffset);
                        n = moveNext(n);

                    } else {
                        waitFor.push(Set.of(DEF_MUSICLIST, DEF_LYRICLIST));
                    }
                }

                if (isLyriclist(n)) {
                    if (!waitFor.isEmpty() && waitFor.peek().contains(DEF_LYRICLIST)) {
                        waitFor.pop();
                        waitForDepth = n.getLevel();
                    }
                    n.changeLevelBy(depthOffset);
                    n = mergeLyriclist(moveNext(n));
                }

                if (!waitFor.isEmpty() && n.getData() != null && waitFor.peek().contains(n.getData().getAction())) {
                    waitFor.pop();
                    depthOffset--;
                }
            }

            String key = n.getData() == null ? n.getName() : n.getData().getAction();
            if (noteAcceptables.containsKey(acceptanceTrace) && noteAcceptables.get(acceptanceTrace).contains(key)) {
                if (acceptanceTrace.isEmpty()) {
                    n.changeLevelBy(1);
                }
                acceptanceTrace = acceptanceTrace.concat(">").concat(key);

            } else {
                acceptanceTrace = "";
            }

            if (!waitFor.isEmpty() && waitFor.peek().contains(n.getName())) {
                waitFor.pop();
                waitForDepth = n.getLevel();

            } else if (waitForDepth > -1 && n.getLevel() == waitForDepth) {
                waitForDepth = -1;
                depthOffset--;
            }

            if (depthOffset != 0) {
                n.changeLevelBy(depthOffset);
            }

            cntOutput++;
            traceNode(n);

            n = n.getNext();
        }

        LOGGER.log(Level.INFO, "transformed {0} LilyNodes to {1} EccoNodes: {2}ms", new Object[] { cntInput, cntOutput, (System.nanoTime() - tm) / 1000000 });
    }

    private static LilypondNode<ParceToken> moveNext(LilypondNode<ParceToken> n) {
        cntOutput++;
        traceNode(n);
        n = n.getNext();
        cntInput++;
        return n;
    }

    private static LilypondNode<ParceToken> moveUntilLevel(LilypondNode<ParceToken> n, int level) {
        assert n != null;

        do {
            n = moveNext(n);
        } while (n != null && n.getLevel() == level);

        return n;
    }

    private static void traceNode(LilypondNode<ParceToken> n) {
        if (!LOGGER.isLoggable(Level.FINER)) return;

        if (n.getData() == null) {
            LOGGER.log(Level.FINER, "{0}({1}) {2}", new Object[] { "* ".repeat(n.getLevel()), cntOutput, n.getName() });
        } else {
            LOGGER.log(Level.FINER, "{0}({1}) {2} {3}", new Object[] { "* ".repeat(n.getLevel()), cntOutput, n.getName(), n.getData().getText() });
        }
    }

    private static boolean isNote(LilypondNode<ParceToken> n) {
        if (n.getData() == null) {
            return false;
        }
        String action = n.getData().getAction();
        return action.equals(DEF_PITCH) || action.equals(DEF_REST);
    }

    private static boolean belongsToNote(LilypondNode<ParceToken> n) {
        return false;
    }

    private static boolean isTransformableVariable(LilypondNode<ParceToken> variableDefinition) {
        LilypondNode<ParceToken> next = variableDefinition.getNext();
        while (next != null && next.getLevel() == variableDefinition.getLevel()) {
            next = next.getNext();
        } // var names like "varOne.varTwo"

        // Delimiter.Operator.Assignment
        if (next == null || next.getData() == null || !next.getData().getAction().equals("Delimiter.Operator.Assignment")) {
            return false;
        }
        next = next.getNext();

        // (LilyPond.list | {Name.Builtin Text.Music.Pitch {LilyPond.pitch}} LilyPond.musiclist | Keyword.Lyric LilyPond.lyriclist)
        if (next == null) {
            return false;
        }
        if (next.getName().equals("LilyPond.list") || next.getName().equals("LilyPond.musiclist")) {
            return true;
        }
        if (next.getData() == null) {
            return false;
        }

        if (next.getData().getAction().equals("Name.Builtin")) {
            return isBuiltinMusiclist(next);
        }
        if (next.getData().getAction().equals(DEF_LYRIC)) {
            return isLyriclist(next);
        }

        return false;
    }

    private static boolean isBuiltinMusiclist(LilypondNode<ParceToken> builtin) {
        // Name.Builtin Text.Music.Pitch {LilyPond.pitch} LilyPond.musiclist
        LilypondNode<ParceToken> next = builtin.getNext();
        if (next == null || next.getData() == null || !next.getData().getAction().equals("Text.Music.Pitch")) {
            return false;
        }
        next = next.getNext();

        if (next != null && next.getName().equals("LilyPond.musiclist")) {
            return true;
        }

        if (next == null || !next.getName().equals("LilyPond.pitch")) {
            return false;
        }
        int lvl = next.getLevel();
        next = next.getNext();
        while (next != null && next.getLevel() > lvl) {
            next = next.getNext();
        }

        return (next != null && next.getName().equals("LilyPond.musiclist"));
    }

    private static boolean isLyriclist(LilypondNode<ParceToken> lyricmode) {
        // Keyword.Lyric LilyPond.lyriclist
        if (!lyricmode.getName().equals(DEF_LYRIC) || !lyricmode.getData().getText().equals("\\lyricmode")) {
            return false;
        }
        lyricmode = lyricmode.getNext();
        if (lyricmode == null || !lyricmode.getName().equals(DEF_LYRICLIST)) {
            return false;
        }
        lyricmode = lyricmode.getNext();
        return lyricmode != null && lyricmode.getData() != null && lyricmode.getData().getAction().equals(DEF_BRACKET_START);
    }

    private static LilypondNode<ParceToken> mergeLyriclist(LilypondNode<ParceToken> lyriclist) {
        int finalDepth = lyriclist.getLevel();
        lyriclist.changeLevelBy(depthOffset);

        LilypondNode<ParceToken> n = moveNext(lyriclist);
        boolean mergeable = n != null && n.getData() != null && n.getData().getAction().equals(DEF_BRACKET_START);
        // n == Bracket-Start (if mergeable)
        if (n == null || n.getLevel() == finalDepth) {
            return n;
        }
        n.changeLevelBy(depthOffset);

        n = moveNext(n);
        if (n == null || n.getLevel() == finalDepth) {
            return n;
        }
        n.changeLevelBy(depthOffset);

        LilypondNode<ParceToken> next = n.getNext();
        while (next != null && next.getLevel() != finalDepth) {
            cntInput++;
            if (n.getData() == null) {
                n = moveNext(n);
                n.changeLevelBy(depthOffset);
                while (n.getLevel() > finalDepth + depthOffset + 1) {
                    if (n.getNext() == null) {
                        return n;
                    }
                    n = moveNext(n);
                    n.changeLevelBy(depthOffset);
                }
                next = n.getNext();
            }
            if (mergeable && n.getData() != null && next.getLevel() == finalDepth + 1 && next.getData() != null && !next.getData().getAction().equals(DEF_BRACKET_END)) {
                ParceToken t = n.getData();
                next.cut();
                t.setText(t.getText().concat(t.getPostWhitespace().concat(next.getData().getText())));
                t.setPostWhitespace(next.getData().getPostWhitespace());

            } else {
                cntOutput++;
                traceNode(n);

                n = next;
                n.changeLevelBy(depthOffset);
                cntOutput++;
                traceNode(n);
            }

            next = n.getNext();
        }

        return next;
    }

    private static void initNoteAcceptables() {
        //noteAcceptables.put("", Set.of("Text.Music.Pitch", "Text.Music.Rest"));
        noteAcceptables.put("", Set.of());

        // Text.Music.Pitch
        noteAcceptables.put(">Text.Music.Pitch", Set.of("LilyPond.pitch", "Literal.Number.Duration"));
        noteAcceptables.put(">Text.Music.Pitch>LilyPond.pitch", Set.of("Text.Music.Pitch.Octave"));
        noteAcceptables.put(">Text.Music.Pitch>LilyPond.pitch>Text.Music.Pitch.Octave", Set.of("Literal.Number.Duration", "Delimiter.Direction"));
        noteAcceptables.put(">Text.Music.Pitch>LilyPond.pitch>Text.Music.Pitch.Octave>Literal.Number.Duration", Set.of("Name.Builtin.Dynamic"));

        noteAcceptables.put(">Text.Music.Pitch>LilyPond.pitch>Text.Music.Pitch.Octave>Delimiter.Direction", Set.of("LilyPond.script"));
        noteAcceptables.put(">Text.Music.Pitch>LilyPond.pitch>Text.Music.Pitch.Octave>Delimiter.Direction>LilyPond.script", Set.of("Literal.Character.Script"));

        noteAcceptables.put(">Text.Music.Pitch>Literal.Number.Duration", Set.of("Delimiter.Direction")); // "Name.Builtin.Dynamic"
        noteAcceptables.put(">Text.Music.Pitch>Literal.Number.Duration>Delimiter.Direction", Set.of("LilyPond.script"));
        noteAcceptables.put(">Text.Music.Pitch>Literal.Number.Duration>Delimiter.Direction>LilyPond.script", Set.of("Literal.Character.Script"));

        // Text.Music.Rest
        noteAcceptables.put(">Text.Music.Rest", Set.of("Literal.Number.Duration"));
        noteAcceptables.put(">Text.Music.Rest>Literal.Number.Duration", Set.of("LilyPond.duration"));
        noteAcceptables.put(">Text.Music.Rest>Literal.Number.Duration>LilyPond.duration", Set.of("LilyPond.duration_scaling"));
        noteAcceptables.put(">Text.Music.Rest>Literal.Number.Duration>LilyPond.duration>LilyPond.duration_scaling", Set.of("Literal.Number.Duration"));
        noteAcceptables.put(">Text.Music.Rest>Literal.Number.Duration>LilyPond.duration>LilyPond.duration_scaling>Literal.Number.Duration",
                Set.of("Literal.Number.Duration.Scaling"));
    }
}
