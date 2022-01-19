package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parce.NodesDeserializer;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.adapter.lilypond.parce.py4j.FileParser;
//import at.jku.isse.ecco.adapter.lilypond.parce.graalvm.FileParser;

public class ParserFactory {
    private static boolean parseFiles = true;

    public static void setParseFiles(boolean flag) {
        parseFiles = flag;
    }

    public static LilypondParser<ParceToken> getParser() {
        if (parseFiles) {
            return new FileParser();

        } else {
            return new NodesDeserializer();
        }
    }
}
