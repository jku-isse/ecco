package at.jku.isse.ecco.adapter.python;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PythonParserFactory {
    protected static final Logger LOGGER = Logger.getLogger(PythonPlugin.class.getName());

    public static PythonParser.Reader getParser() {

        try {
            Class<?> clazz = Class.forName("at.jku.isse.ecco.adapter.python.parse.py4j.ast.reader.PY4JASTReadParser", false,
                    PythonParserFactory.class.getClassLoader());
            return (PythonParser.Reader) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) { }

        try {
            Class<?> clazz = Class.forName("at.jku.isse.ecco.adapter.python.parse.py4j.cst.reader.PY4JCSTReadParser", false,
                    PythonParserFactory.class.getClassLoader());
            return (PythonParser.Reader) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) { }

        LOGGER.log(Level.SEVERE, "no read parser found. configure Py4J- or GraalVM-Parser");
        return null;
    }

    public static PythonParser.Writer getWriteParser() {

        try {
            Class<?> clazz = Class.forName("at.jku.isse.ecco.adapter.python.parse.py4j.ast.writer.PY4JASTWriteParser", false,
                    PythonParserFactory.class.getClassLoader());
            return (PythonParser.Writer) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) { }

        try {
            Class<?> clazz = Class.forName("at.jku.isse.ecco.adapter.python.parse.py4j.cst.writer.PY4JCSTWriteParser", false,
                    PythonParserFactory.class.getClassLoader());
            return (PythonParser.Writer) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) { }

        LOGGER.log(Level.SEVERE, "no write parser found. configure Py4J- or GraalVM-Parser");
        return null;
    }
}
