package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.lilypond.LilyEccoTransformer;
import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TransformerTest {

    @Test(groups = {"integration", "lilypond"})
    public void RestScalingDepth_Test() {
        LilypondNode<ParceToken> h = new LilypondNode<>("LilyPond.musiclist", null);
        h.setLevel(0);

        LilypondNode<ParceToken> p;
        LilypondNode<ParceToken> n = new LilypondNode<>("Text.Music.Rest", new ParceToken(0, "R", "Text.Music.Rest"));
        h.append(n, 1);
        p = n;

        n = new LilypondNode<>("Literal.Number.Duration", new ParceToken(0, "1", "Literal.Number.Duration"));
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("LilyPond.duration", null);
        p.append(n, 1);
        p = n;

        n = new LilypondNode<>("LilyPond.duration_scaling", null);
        p.append(n, 2);
        p = n;

        n = new LilypondNode<>("Literal.Number.Duration", new ParceToken(0, "*", "Literal.Number.Duration"));
        p.append(n, 3);
        p = n;

        n = new LilypondNode<>("Literal.Number.Duration.Scaling", new ParceToken(0, "3", "Literal.Number.Duration.Scaling"));
        p.append(n, 3);

        LilyEccoTransformer.transform(h);

        Assert.assertEquals(h.getLevel(), 0); // musiclist
        n = h.getNext();

        Assert.assertEquals(n.getLevel(), 1); // R
        n = n.getNext();

        Assert.assertEquals(n.getLevel(), 2); // 1
        n = n.getNext();

        Assert.assertEquals(n.getLevel(), 2); // duration
        n = n.getNext();

        Assert.assertEquals(n.getLevel(), 3); // duration_scaling
        n = n.getNext();

        Assert.assertEquals(n.getLevel(), 4); // *
        n = n.getNext();

        Assert.assertEquals(n.getLevel(), 4); // 3
    }

}
