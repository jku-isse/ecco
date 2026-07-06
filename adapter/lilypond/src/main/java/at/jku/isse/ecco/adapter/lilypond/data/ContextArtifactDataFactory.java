package at.jku.isse.ecco.adapter.lilypond.data;

import at.jku.isse.ecco.adapter.lilypond.data.context.*;

public class ContextArtifactDataFactory {

    /**
     * Creates new lilypond context artifact data for given context.
     * Will return a {@link DefaultContextArtifactData} when no specific class is available.
     * @param context Name of context in python <a href="https://parce.info">parce</a> parser (lilypond <a href="https://github.com/wbsoft/parce/blob/master/parce/lang/lilypond.py">lexicon</a>
     */
    public static BaseContextArtifactData getContextArtifactData(String context) {

        switch (context) {
            case "LilyPond.singleline_comment":
            case "LilyPond.multiline_comment":
                return new CommentContextArtifactData(context);

            case "LilyPond.lyriclist":
                return new LyricContextArtifactData(context);

            default:
                return new DefaultContextArtifactData(context);
        }
    }
}
