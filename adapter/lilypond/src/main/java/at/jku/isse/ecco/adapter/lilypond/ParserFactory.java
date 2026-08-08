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

    public static LilypondParser<ParceToken> getParser() {
        if (parseFiles) {
            LilypondParser<ParceToken> parser = tryInstantiate("at.jku.isse.ecco.adapter.lilypond.parce.py4j.FileParser");
            if (parser != null) {
                return parser;
            }

            parser = tryInstantiate("at.jku.isse.ecco.adapter.lilypond.parce.graalvm.FileParser");
            if (parser != null) {
                return parser;
            }

        } else {
            return new NodesDeserializer();
        }

        LOGGER.log(Level.SEVERE, "no parser found. configure Py4J- or GraalVM-Parser");

        return null;
    }

    @SuppressWarnings("unchecked")
    private static LilypondParser<ParceToken> tryInstantiate(String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, ParserFactory.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            // this backend simply isn't on the classpath - expected, not an error; the caller
            // falls back to trying the next one.
            return null;
        }

        try {
            return (LilypondParser<ParceToken>) clazz.getDeclaredConstructor().newInstance();
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
