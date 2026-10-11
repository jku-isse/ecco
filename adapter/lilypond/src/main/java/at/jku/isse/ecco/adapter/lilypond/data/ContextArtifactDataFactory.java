package at.jku.isse.ecco.adapter.lilypond.data;

import at.jku.isse.ecco.adapter.lilypond.data.context.*;

public class ContextArtifactDataFactory {

    /**
     * Creates new lilypond context artifact data for given context.
     * Will return a {@link DefaultContextArtifactData} when no specific class is available.
     * @param context Name of a context the parser sends (LilyPond.musiclist, SchemeLily.list - named as parce named its lexicons)
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
