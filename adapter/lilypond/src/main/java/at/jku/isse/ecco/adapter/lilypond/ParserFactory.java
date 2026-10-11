package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parser.NodesDeserializer;
import at.jku.isse.ecco.adapter.lilypond.parser.LilypondToken;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParserFactory {
    protected static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private static boolean parseFiles = true;
    public static void setParseFiles(boolean flag) {
        parseFiles = flag;
    }

    public static LilypondParser<LilypondToken> getParser() {
        if (parseFiles) {
            LilypondParser<LilypondToken> parser = tryInstantiate("at.jku.isse.ecco.adapter.lilypond.parser.py4j.FileParser");
            if (parser != null) {
                return parser;
            }

        } else {
            return new NodesDeserializer();
        }

        LOGGER.log(Level.SEVERE, "no parser found: the py4j FileParser could not be instantiated");

        return null;
    }

    @SuppressWarnings("unchecked")
    private static LilypondParser<LilypondToken> tryInstantiate(String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, ParserFactory.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            // this backend simply isn't on the classpath - expected, not an error; the caller
            // falls back to trying the next one.
            return null;
        }

        try {
            return (LilypondParser<LilypondToken>) clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            // the backend IS present but failed to instantiate - a real, fixable configuration
            // problem (missing native dependency, gateway misconfiguration, ...), distinct from
            // "not installed" - log it with the actual cause instead of silently discarding it,
            // same as ClassNotFoundException would otherwise make it look. Catches Throwable, not
            // just Exception: a present-but-broken backend's own missing transitive dependency
            // surfaces as NoClassDefFoundError (an Error, confirmed via a real py4j-less run), not
            // an Exception - narrower catches let it straight through uncaught.
            LOGGER.log(Level.WARNING, "found parser backend " + className + " but could not instantiate it", e);
            return null;
        }
    }
}
