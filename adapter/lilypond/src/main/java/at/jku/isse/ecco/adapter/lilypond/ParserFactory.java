package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parce.GraalVMFileParser;
import at.jku.isse.ecco.adapter.lilypond.parce.NodesDeserializer;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.adapter.lilypond.parce.Py4jFileParser;

public class ParserFactory {
    private static boolean parseFiles = true;

    public static void setParseFiles(boolean flag) {
        parseFiles = flag;
    }

    public static LilypondParser<ParceToken> getParser() {
        if (parseFiles) {
            return new Py4jFileParser();
            //return new GraalVMFileParser();

        } else {
            return new NodesDeserializer();
        }
    }
}
