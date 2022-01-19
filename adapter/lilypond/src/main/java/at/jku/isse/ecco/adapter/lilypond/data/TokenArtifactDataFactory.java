package at.jku.isse.ecco.adapter.lilypond.data;

import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.data.token.*;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

public class TokenArtifactDataFactory {

    /**
     * Creates new lilypond token artifact data for given token.
     * Will return a {@link DefaultTokenArtifactData} when no specific class is available.
     * @param token Token from <a href="https://parce.info">parce</a> parser
     */
    public static DefaultTokenArtifactData getTokenArtifactData(ParceToken token) {

        switch (token.getAction()) {
            case "Name.Variable.Definition":
                return new VariableDefinitionArtifactData(token);

            case LilypondReader.PARSER_ACTION_LINEBREAK:
                return new LineBreakArtifactData(token);

            default:
                return new DefaultTokenArtifactData(token);
        }
    }
}
