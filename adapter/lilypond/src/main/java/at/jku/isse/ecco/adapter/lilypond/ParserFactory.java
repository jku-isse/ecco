package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parce.NodesDeserializer;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParserFactory {
    protected static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private static boolean parseFiles = true;
    public static void setParseFiles(boolean flag) {
        parseFiles = flag;
    }

    @SuppressWarnings("unchecked")
    public static LilypondParser<ParceToken> getParser() {
        if (parseFiles) {
            try {
                Class<?> clazz = Class.forName("at.jku.isse.ecco.adapter.lilypond.parce.py4j.FileParser", false,
                        ParserFactory.class.getClassLoader());
                return (LilypondParser<ParceToken>) clazz.getDeclaredConstructor().newInstance();

            } catch (Exception ignored) {}

            try {
                Class<?> clazz = Class.forName("at.jku.isse.ecco.adapter.lilypond.parce.graalvm.FileParser", false,
                        ParserFactory.class.getClassLoader());
                return (LilypondParser<ParceToken>) clazz.getDeclaredConstructor().newInstance();

            } catch (Exception ignored) {}

        } else {
            return new NodesDeserializer();
        }

        LOGGER.log(Level.SEVERE, "no parser found. configure Py4J- or GraalVM-Parser");

        return null;
    }
}
