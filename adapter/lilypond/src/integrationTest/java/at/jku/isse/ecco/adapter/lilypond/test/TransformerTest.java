package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilyEccoTransformer;
import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.LilypondStringWriter;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.*;

public class TransformerTest {
    Logger LOGGER = LilypondReader.getLogger();

    @Test(groups = {"transformation"})
    @BeforeClass(alwaysRun = true)
    private void setUp() {
        LOGGER.setLevel(Level.ALL);
        try {
            FileHandler handler = new FileHandler("test.log");
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private LilypondNode<ParceToken> append(LilypondNode<ParceToken> n, int level, int pos, String text, String action) {
        return n.append(action, new ParceToken(pos, text, action), level);
    }

    @Test(groups = {"transformation"})
    public void transform_VariableDefinition_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("LilyPond.list", null);
        h.setLevel(0);

        LilypondNode<ParceToken> n = append(h, 1, 0, "lyricsVar", "Name.Variable.Definition");

        n = append(n, 0, 10, "=", "Delimiter.Operator.Assignment");
        append(n, 0, 12, "\\relative", "Name.Builtin");

        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0); // musiclist
        Assert.assertEquals(h.getName(), "lyricsVar");
        Assert.assertEquals(h.getData().getText(), "lyricsVar =");
        n = h.getNext();

        Assert.assertEquals(n.getLevel(), 0); // \relative
        Assert.assertEquals(n.getData().getText(), "\\relative");

        Assert.assertNull(n.getNext());
    }

    @Test(groups = {"transformation"})
    public void transform_IdentifierSingleVariable_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("LilyPond.identifier_ref", null);
        h.setLevel(0);

        LilypondNode<ParceToken> n = append(h, 1, 0, "\\variableName", "Name.Variable");

        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0);
        Assert.assertEquals(h.getName(), "LilyPond.identifier_ref");

        Assert.assertEquals(n.getData().getText(), "\\variableName");

        Assert.assertNull(n.getNext());
    }

    @Test(groups = {"transformation"})
    public void transform_IdentifierMultiVariables_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("LilyPond.identifier_ref", null);
        h.setLevel(0);

        LilypondNode<ParceToken> n = append(h, 1, 0, "\\variableName1", "Name.Variable");
        n = append(n, 1, 14, ".", "Delimiter.Seperator");
        n = append(n, 1, 15, "variableName2", "Name.Variable");

        append(n, 0, 27, "{", "Bracket.Start");

        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0);
        Assert.assertEquals(h.getName(), "LilyPond.identifier_ref");

        Assert.assertEquals(h.getData().getText(), "\\variableName1.variableName2");

        n =  h.getNext();
        Assert.assertEquals(n.getName(), "Bracket.Start");
        Assert.assertEquals(n.getData().getPos(), 27);
    }

    @Test(groups = {"transformation"})
    public void transform_LyricList_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("=", new ParceToken(0, "=", "Delimiter.Operator.Assignment"));
        h.setLevel(0);

        LilypondNode<ParceToken> n = append(h, 0, 2, "\\lyricmode", "Keyword.Lyric");
        n = n.append("LilyPond.lyriclist", null, 0);

        n = append(n, 1, 13, "{", "Delimiter.Bracket.Start");
        n = append(n, 1, 15, "Glo", "Text.Lyric.LyricText");
        n = append(n, 1, 19, "--", "Delimiter.Lyric.LyricHyphen");
        n = append(n, 1, 22, "ria", "Text.Lyric.LyricText");
        n = append(n, 1, 26, "}", "Delimiter.Bracket.End");

        append(n, 0, 28, "\\relative", "Name.Builtin");

        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0); // assignment
        Assert.assertEquals(h.getName(), "=");
        n = h.getNext();

        Assert.assertEquals(n.getName(), "Keyword.Lyric");
        Assert.assertEquals(n.getData().getText(), "\\lyricmode { Glo -- ria } ");
        Assert.assertEquals(n.getData().getAction(), "Text.Lyric.LyricText");
        Assert.assertEquals(n.getLevel(), 0); // \relative
        n = n.getNext();

        Assert.assertEquals(n.getName(), "Name.Builtin");
    }

    @Test(groups = {"transformation"})
    public void transform_LyricsTo_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("Keyword", new ParceToken(0, "\\score", "Keyword"));
        h.setLevel(0);

        LilypondNode<ParceToken> n = append(h, 0, 7, "{", "Delimiter.Bracket.Start");
        n = n.append("LilyPond.musiclist", null, 0);

        n = append(n, 1, 11, "<<", "Delimiter.Bracket.Start");
        n = append(n, 1, 18, "\\new", "Keyword");
        n = n.append("LilyPond.list", null, 1);

        n = append(n, 2, 23, "Lyrics", "Name.Constant.Context");

        n = append(n, 1, 30, "=", "Delimiter.Operator.Assignment");
        n = n.append("LilyPond.list", null, 1);

        n = n.append("LilyPond.string", null, 2);

        n = append(n, 3,32, "\"", "Literal.String");
        n = append(n, 3, 33, "lyrics", "Literal.String");
        n = append(n, 3, 39, "\"", "Literal.String");

        n = n.append("LilyPond.layout_context", null, 1);

        n = append(n, 2, 41, "\\with", "Keyword");
        n = append(n, 2,47, "{", "Delimiter.Bracket.Start");
        n = append(n, 2,55, "\\override", "Keyword");
        n = n.append("LilyPond.list", null, 2);

        n = append(n, 3, 65, "VerticalAxisGroup", "Name.Object.Grob");
        n = append(n, 3, 82, ".", "Delimiter.Separator");
        n = append(n, 3, 83, "staff-affinity", "Name.Variable");

        n = append(n, 2, 98, "=", "Delimiter.Operator.Assignment");
        n = n.append("LilyPond.list", null, 2);

        n = n.append("SchemeLily.scheme", null, 3);

        n = append(n, 4, 100, "#", "Delimiter.ModeChange.SchemeStart");
        n = n.append("SchemeLily.argument", null, 4);

        n = append(n, 5, 101, "DOWN", "Name");

        n = append(n, 2, 110, "}", "Delimiter.Bracket.End");

        n = append(n, 1, 116, "\\new", "Keyword");
        n = n.append("LilyPond.list", null, 1);

        n = append(n, 2, 121, "Staff", "Name.Constant.Context");

        n = n.append("LilyPond.musiclist", null, 1);

        n = append(n, 2,127, "{", "Delimiter.Bracket.Start");
        n = append(n, 2,135, "\\new", "Keyword");
        n = n.append("LilyPond.list", null, 2);

        n = append(n, 3,140, "Voice", "Name.Constant.Context");

        n = append(n, 2,146, "=", "Delimiter.Operator.Assignment");
        n = n.append("LilyPond.list", null, 2);

        n = n.append("LilyPond.string", null, 3);

        n = append(n, 4,148, "\"", "Literal.String");
        n = append(n, 4,149, "melody", "Literal.String");
        n = append(n, 4,155, "\"", "Literal.String");

        n = n.append("LilyPond.musiclist", null, 2);

        n = append(n, 3,157, "{", "Delimiter.Bracket.Start");
        n = append(n, 3,167, "\\relative", "Name.Builtin");
        n = n.append("LilyPond.musiclist", null, 3);

        n = append(n, 4,177, "{", "Delimiter.Bracket.Start");
        n = append(n, 4,179, "c", "Text.Music.Pitch");
        n = n.append("LilyPond.pitch", null, 4);

        n = append(n, 5,180, "''", "Text.Music.Pitch.Octave");

        n = append(n, 4,182, "4", "Literal.Number.Duration");
        n = append(n, 4,184, "c", "Text.Music.Pitch");
        n = append(n, 4,186, "c", "Text.Music.Pitch");
        n = append(n, 4,188, "c", "Text.Music.Pitch");
        n = append(n, 4,190, "}", "Delimiter.Bracket.End");

        n = append(n, 3,198, "}", "Delimiter.Bracket.End");

        n = append(n, 2,204, "}", "Delimiter.Bracket.End");

        n = append(n, 1,210, "\\context", "Keyword");
        n = n.append("LilyPond.list", null, 1);

        n = append(n, 2,219, "Lyrics", "Name.Constant.Context");

        n = append(n, 1,226, "=", "Delimiter.Operator.Assignment");
        n = n.append("LilyPond.list", null, 1);

        n =n.append("LilyPond.string", null, 2);

        n = append(n, 3,228, "\"", "Literal.String");
        n = append(n, 3,229, "lyrics", "Literal.String");
        n = append(n, 3,235, "\"", "Literal.String");

        n = n.append("LilyPond.musiclist", null, 1);

        n = append(n, 2,237, "{", "Delimiter.Bracket.Start");
        n = append(n, 2,245, "\\lyricsto", "Keyword.Lyric");
        n = n.append("LilyPond.lyricsto", null, 2);

        n = n.append("LilyPond.list", null, 3);

        n = n.append("LilyPond.string", null, 4);

        n = append(n, 5,255, "\"", "Literal.String");
        n = append(n, 5,256, "melody", "Literal.String");
        n = append(n, 5,262, "\"", "Literal.String");

        n = n.append("LilyPond.lyriclist", null, 2);

        n = append(n, 3,264, "{", "Delimiter.Bracket.Start");
        n = append(n, 3,274, "Here", "Text.Lyric.LyricText");
        n = append(n, 3,274, "are", "Text.Lyric.LyricText");
        n = append(n, 3,274, "the", "Text.Lyric.LyricText");
        n = append(n, 3,274, "words", "Text.Lyric.LyricText");
        n = append(n, 3,299, "}", "Delimiter.Bracket.End");

        n = append(n, 2,305, "}", "Delimiter.Bracket.End");

        n = append(n, 1, 309, ">>", "Delimiter.Bracket.End");

        append(n, 0, 312, "}", "Delimiter.Bracket.End");

        h = LilyEccoTransformer.transform(h);
        n = h;

        assert n != null;
        Assert.assertEquals(n.getLevel(), 0);
        Assert.assertEquals(n.getName(), "Keyword");
        Assert.assertEquals(n.getData().getText(), "\\score");

        while (n != null && !n.getName().equals("LilyPond.string")) {
            n = n.getNext();
        }
        if (n == null) {
            Assert.fail("string-node LilyPond.string not found [first occurrence of \"lyrics\"]");
        }
        Assert.assertEquals(n.getData().getText(), "\"lyrics\"");
        n = n.getNext();

        while (n != null && !n.getName().equals("LilyPond.string")) {
            n = n.getNext();
        }
        if (n == null) {
            Assert.fail("string-node LilyPond.string not found [first occurrence of \"melody\"");
        }
        Assert.assertEquals(n.getData().getText(), "\"melody\"");
        n = n.getNext();

        while (n != null && !n.getName().equals("LilyPond.string")) {
            n = n.getNext();
        }
        if (n == null) {
            Assert.fail("string-node LilyPond.string not found [second occurrence of \"lyrics\"]");
        }
        Assert.assertEquals(n.getData().getText(), "\"lyrics\"");
        n = n.getNext();

        while (n != null && !n.getName().equals("Keyword.Lyric")) {
            n = n.getNext();
        }
        if (n == null) {
            Assert.fail("LilyPond.lyricsto not found");
        }

        Assert.assertEquals(n.getName(), "Keyword.Lyric");
        Assert.assertEquals(n.getData().getText(), "\\lyricsto \"melody\" { Here are the words } ");
        Assert.assertEquals(n.getData().getAction(), "Text.Lyric.LyricText");
        Assert.assertEquals(n.getLevel(), 2);
        n = n.getNext();

        Assert.assertEquals(n.getName(), "Delimiter.Bracket.End");

        EntityFactory ef = new MemEntityFactory();
        LilypondReader rd = new LilypondReader(ef);
        Artifact.Op<PluginArtifactData> pluginArtifact = ef.createArtifact(new PluginArtifactData(rd.getPluginId(), Path.of("")));
        Node.Op pluginNode = ef.createOrderedNode(pluginArtifact);
        rd.generateEccoTree(h, pluginNode);

        Set<Node> nodes = new HashSet<>();
        nodes.add(pluginNode);

        LilypondStringWriter w = new LilypondStringWriter();
        LOGGER.log(Level.FINE, String.join(System.lineSeparator(), w.write(nodes)));
    }
}
