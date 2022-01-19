package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LilyEccoTransformer {
    private static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private final static String DEF_VARIABLE = "Name.Variable.Definition";
    private final static String DEF_IDENTIFIER = "LilyPond.identifier_ref";
    private final static String DEF_LYRIC = "Keyword.Lyric";
    private final static String DEF_LYRICLIST = "LilyPond.lyriclist";
    private final static String DEF_BRACKET_START = "Delimiter.Bracket.Start";
    private final static String DEF_STRING = "LilyPond.string";
    private final static String DEF_SCHEME_STRING = "SchemeLily.string";
    private final static String DEF_LITERAL_STRING = "Literal.String";
    private final static String DEF_SCHEME_NUMBER = "SchemeLily.number";

    private static int cntInput;
    private static int cntOutput;

    public static LilypondNode<ParceToken> transform(LilypondNode<ParceToken> head) {
        if (null == head) {
            LOGGER.warning("first node is null");
            return null;
        }

        LilypondNode<ParceToken> holder = new LilypondNode<>("HOLDER", null);
        holder.setLevel(0);
        holder.setNext(head);
        head.setPrev(holder);

        cntInput = 0;
        cntOutput = 0;

        long tm = System.nanoTime();
        LOGGER.finer("\n\n****** START TRANSFORMATION ******\n");

        LilypondNode<ParceToken> n = head;
        while (n != null) {
            if (n.getData() != null) {
                if (n.getData().getAction().equals(DEF_VARIABLE)) {
                    n = transformVariableDefinitonNode(n);
                }

                if (isLyriclist(n)) {
                    n = transformLyriclist(n);
                }

            } else if (n.getName().equals(DEF_IDENTIFIER)) {
                n = transformIdentifier(n);
            } else if (n.getName().equals(DEF_STRING) || n.getName().equals(DEF_SCHEME_STRING)) {
                n = transformString(n, n.getName());
            } else if (n.getName().equals(DEF_SCHEME_NUMBER)) {
                n = transformSchemeNumber(n);
            }

            n = moveNext(n);
        }

        LOGGER.log(Level.INFO, "transformed {0} input to {1} output nodes: {2}ms", new Object[] { cntInput, cntOutput, (System.nanoTime() - tm) / 1000000 });

        head = holder.getNext();
        head.setPrev(null);
        holder.setNext(null);
        return head;
    }

    private static LilypondNode<ParceToken> moveNext(LilypondNode<ParceToken> n) {
        cntOutput++;
        traceNode(n);
        n = n.getNext();
        cntInput++;
        return n;
    }

    private static void traceNode(LilypondNode<ParceToken> n) {
        if (!LOGGER.isLoggable(Level.FINER)) return;

        if (n.getData() == null) {
            LOGGER.log(Level.FINER, "{0}({1}) \"{2}\"", new Object[] { "* ".repeat(n.getLevel()), cntOutput, n.getName() });
        } else {
            LOGGER.log(Level.FINER, "{0}({1}) {2} \"{3}\"", new Object[] { "* ".repeat(n.getLevel()), cntOutput, n.getName(), n.getData().getText() });
        }
    }

    private static LilypondNode<ParceToken> transformVariableDefinitonNode(LilypondNode<ParceToken> defNode) {
        if (defNode.getPrev() == null || !defNode.getPrev().getName().equals("LilyPond.list")) {
            return defNode;
        }
        LilypondNode<ParceToken> prev = defNode.getPrev().getPrev();

        StringBuilder variableDefinition = new StringBuilder(defNode.getData().getText());
        StringBuilder fullVarDefinition = new StringBuilder(defNode.getData().getText());
        LilypondNode<ParceToken> next = defNode.getNext();
        cntInput++;
        while (next != null && next.getLevel() == defNode.getLevel()) {
            variableDefinition.append(next.getData().getText());
            fullVarDefinition.append(next.getData().getText());
            next = next.getNext();
            cntInput++;
        } // var names like "varOne.varTwo"

        // Delimiter.Operator.Assignment
        if (next == null || next.getData() == null || !next.getData().getAction().equals("Delimiter.Operator.Assignment")) {
            return defNode;
        }
        fullVarDefinition.append(next.getData().getText());

        ParceToken t = new ParceToken(defNode.getData().getPos(),
                fullVarDefinition.toString(), DEF_VARIABLE, variableDefinition.toString());
        LilypondNode<ParceToken> def = new LilypondNode<>(variableDefinition.toString(), t);
        def.setLevel(defNode.getPrev().getLevel());
        prev.setNext(def);  cntOutput--; // replaced LilyPond.list
        def.setPrev(prev);
        def.setNext(next.getNext());
        def.getNext().setPrev(def);

        return def;
    }

    private static LilypondNode<ParceToken> transformIdentifier(LilypondNode<ParceToken> identifier) {
        LilypondNode<ParceToken> n = identifier.getNext();
        cntInput++;
        StringBuilder sb = new StringBuilder();
        ParceToken newToken = null;
        while (n != null && n.getLevel() > identifier.getLevel()) {
            if (n.getData() != null) {
                if (newToken == null) { newToken = new ParceToken(n.getData().getPos(), "", n.getData().getAction()); }
                sb.append(n.getData().getText());
            }

            n = n.getNext();
            cntInput++;
        }

        if (newToken != null) {
            newToken.setText(sb.toString());
            LilypondNode<ParceToken> newNode = new LilypondNode<>(DEF_IDENTIFIER, newToken);
            newNode.setLevel(identifier.getLevel());
            newNode.setPrev(identifier.getPrev());
            identifier.getPrev().setNext(newNode);
            newNode.setNext(n);
            if (n != null) { n.setPrev(newNode); }

            return newNode;

        } else {
            return identifier;
        }
    }

    private static LilypondNode<ParceToken> transformString(LilypondNode<ParceToken> string, String tokenName) {
        LilypondNode<ParceToken> n = string.getNext();
        if (n.getData() == null || !n.getData().getAction().equals(DEF_LITERAL_STRING)) { return string; }
        cntInput++;
        StringBuilder sb = new StringBuilder();
        ParceToken newToken = null;
        while (n != null && n.getLevel() > string.getLevel()) {
            if (n.getData() != null) {
                if (newToken == null) { newToken = new ParceToken(n.getData().getPos(), "", n.getData().getAction()); }
                sb.append(n.getData().getText());
            }

            n = n.getNext();
            cntInput++;
        }

        if (newToken != null) {
            newToken.setText(sb.toString());
            LilypondNode<ParceToken> newNode = new LilypondNode<>(tokenName, newToken);
            newNode.setLevel(string.getLevel());
            newNode.setPrev(string.getPrev());
            string.getPrev().setNext(newNode);
            newNode.setNext(n);
            if (n != null) { n.setPrev(newNode); }

            return newNode;

        } else {
            return string;
        }
    }

    private static LilypondNode<ParceToken> transformSchemeNumber(LilypondNode<ParceToken> number) {
        LilypondNode<ParceToken> n = number.getNext();
        if (n.getData() == null || !n.getData().getAction().startsWith("Literal.Number")) { return number; }
        cntInput++;
        StringBuilder sb = new StringBuilder();
        ParceToken newToken = null;
        while (n != null && n.getLevel() > number.getLevel()) {
            if (n.getData() != null) {
                if (newToken == null) { newToken = new ParceToken(n.getData().getPos(), "", n.getData().getAction()); }
                sb.append(n.getData().getText());
            }

            n = n.getNext();
            cntInput++;
        }

        if (newToken != null) {
            newToken.setText(sb.toString());
            LilypondNode<ParceToken> newNode = new LilypondNode<>(DEF_SCHEME_NUMBER, newToken);
            newNode.setLevel(number.getLevel());
            newNode.setPrev(number.getPrev());
            number.getPrev().setNext(newNode);
            newNode.setNext(n);
            if (n != null) { n.setPrev(newNode); }

            return newNode;

        } else {
            return number;
        }
    }

    private static boolean isLyriclist(LilypondNode<ParceToken> lyricmode) {
        // pattern: Keyword.Lyric LilyPond.lyriclist
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

    private static LilypondNode<ParceToken> transformLyriclist(LilypondNode<ParceToken> lyricmode) {
        LilypondNode<ParceToken> n = lyricmode.getNext().getNext();
        if (n == null || n.getData() == null || !n.getData().getAction().equals(DEF_BRACKET_START)) {
            return lyricmode;
        }
        cntInput++;
        cntInput++;

        LilypondNode<ParceToken> prev = lyricmode.getPrev();
        StringBuilder lyrics = new StringBuilder(lyricmode.getData().getText());
        lyrics.append(" ");
        while (n != null && n.getLevel() > lyricmode.getLevel()) {
            if (n.getData() != null) {
                if (n.getData().getAction().equals(LilypondReader.PARSER_ACTION_LINEBREAK)) {
                    lyrics.append("\n").append(n.getData().getText());
                } else {
                    lyrics.append(n.getData().getText()).append(" ");
                }
            }

            n = n.getNext();
            cntInput++;
        }

        ParceToken t = new ParceToken(lyricmode.getData().getPos(), lyrics.toString(), "Text.Lyric.LyricText");
        LilypondNode<ParceToken> ly = new LilypondNode<>(DEF_LYRIC, t);
        ly.setLevel(prev.getLevel());
        prev.setNext(ly);
        ly.setPrev(prev);
        ly.setNext(n);
        if (n != null) { n.setPrev(ly); }

        return ly;
    }
}
