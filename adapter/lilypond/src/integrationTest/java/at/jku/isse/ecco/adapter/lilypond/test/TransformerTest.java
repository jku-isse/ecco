package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.lilypond.LilyEccoTransformer;
import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.logging.*;

public class TransformerTest {

    @Test(groups = {"transformation"})
    @BeforeClass(alwaysRun = true)
    private void setUp() {
        Logger log = LilypondReader.getLogger();
        log.setLevel(Level.ALL);
        try {
            FileHandler handler = new FileHandler("test.log");
            handler.setFormatter(new SimpleFormatter());
            log.addHandler(handler);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test(groups = {"transformation"})
    public void VariableDefinition_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("LilyPond.list", null);
        h.setLevel(0);

        LilypondNode<ParceToken> p;
        LilypondNode<ParceToken> n = new LilypondNode<>("Name.Variable.Definition", new ParceToken(0, "lyricsVar", "Name.Variable.Definition"));
        n.getData().setPostWhitespace(" ");
        h.append(n, 1);
        p = n;

        n = new LilypondNode<>("=", new ParceToken(10, "=", "Delimiter.Operator.Assignment"));
        n.getData().setPostWhitespace(" ");
        p.append(n, 0);
        p = n;

        n = new LilypondNode<>("Name.Builtin", new ParceToken(12, "\\relative", "Name.Builtin"));
        p.append(n, 0);

        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0); // musiclist
        Assert.assertEquals(h.getName(), "lyricsVar");
        Assert.assertEquals(h.getData().getFullText(), "lyricsVar = ");
        n = h.getNext();

        Assert.assertEquals(n.getLevel(), 0); // \relative
        Assert.assertEquals(n.getData().getText(), "\\relative");

        Assert.assertNull(n.getNext());
    }

    @Test(groups = {"transformation"})
    public void IdentifierSingleVariable_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("LilyPond.identifier_ref", null);
        h.setLevel(0);

        LilypondNode<ParceToken> n = new LilypondNode<>("\\variableName", new ParceToken(0, "\\variableName", "Name.Variable"));
        n.getData().setPostWhitespace(" ");
        h.append(n, 1);

        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0);
        Assert.assertEquals(h.getName(), "LilyPond.identifier_ref");

        Assert.assertEquals(h.getData().getText(), "\\variableName");
        Assert.assertEquals(h.getData().getPostWhitespace(), " ");
        Assert.assertEquals(h.getData().getFullText(), "\\variableName ");

        Assert.assertNull(h.getNext());
    }

    @Test(groups = {"transformation"})
    public void IdentifierMultiVariables_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("LilyPond.identifier_ref", null);
        h.setLevel(0);

        LilypondNode<ParceToken> n = new LilypondNode<>("\\variableName1", new ParceToken(0, "\\variableName1", "Name.Variable"));
        h.append(n, 1);

        LilypondNode<ParceToken> p = n;
        n = new LilypondNode<>(".", new ParceToken(14, ".", "Delimiter.Seperator"));
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("\\variableName2", new ParceToken(15, "variableName2", "Name.Variable"));
        n.getData().setPostWhitespace(" ");
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("something", new ParceToken(27, "{", "Bracket.Start"));
        p.append(n, 0);

        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0);
        Assert.assertEquals(h.getName(), "LilyPond.identifier_ref");

        Assert.assertEquals(h.getData().getText(), "\\variableName1.variableName2");
        Assert.assertEquals(h.getData().getPostWhitespace(), " ");

        n =  h.getNext();
        Assert.assertEquals(n.getName(), "something");
        Assert.assertEquals(n.getData().getPos(), 27);
    }

    @Test(groups = {"transformation"})
    public void LyricList_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("=", new ParceToken(0, "=", "Delimiter.Operator.Assignment"));
        h.getData().setPostWhitespace(" ");
        h.setLevel(0);

        LilypondNode<ParceToken> p = h;
        LilypondNode<ParceToken> n = new LilypondNode<>("Keyword.Lyric", new ParceToken(2, "\\lyricmode", "Keyword.Lyric"));
        n.getData().setPostWhitespace(" ");
        p.append(n, 0);
        p = n;

        n = new LilypondNode<>("LilyPond.lyriclist", null);
        p.append(n, 0);
        p = n;

        n = new LilypondNode<>("{", new ParceToken(13, "{", "Delimiter.Bracket.Start"));
        n.getData().setPostWhitespace("\n");
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("Glo", new ParceToken(15, "Glo", "Text.Lyric.LyricText"));
        n.getData().setPostWhitespace(" ");
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("--", new ParceToken(19, "--", "Delimiter.Lyric.LyricHyphen"));
        n.getData().setPostWhitespace(" ");
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("ria", new ParceToken(22, "ria", "Text.Lyric.LyricText"));
        n.getData().setPostWhitespace("\n");
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("}", new ParceToken(26, "}", "Delimiter.Bracket.End"));
        n.getData().setPostWhitespace("\n");
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("Name.Builtin", new ParceToken(28, "\\relative", "Name.Builtin"));
        p.append(n, 0);


        h = LilyEccoTransformer.transform(h);

        assert h != null;
        Assert.assertEquals(h.getLevel(), 0); // assignment
        Assert.assertEquals(h.getName(), "=");
        n = h.getNext();

        Assert.assertEquals(n.getName(), "Keyword.Lyric");
        Assert.assertEquals(n.getData().getText(), "\\lyricmode {\nGlo -- ria\n}\n");
        Assert.assertEquals(n.getData().getAction(), "Text.Lyric.LyricText");
        Assert.assertEquals(n.getData().getPostWhitespace(), "");
        Assert.assertEquals(n.getData().getFullText(), "\\lyricmode {\nGlo -- ria\n}\n");
        Assert.assertEquals(n.getLevel(), 0); // \relative
        n = n.getNext();

        Assert.assertEquals(n.getName(), "Name.Builtin");
    }
}
