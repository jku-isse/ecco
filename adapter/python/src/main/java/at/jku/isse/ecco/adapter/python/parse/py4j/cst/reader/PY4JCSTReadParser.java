package at.jku.isse.ecco.adapter.python.parse.py4j.cst.reader;


import at.jku.isse.ecco.adapter.python.PythonParser;
import at.jku.isse.ecco.adapter.python.parse.py4j.PY4JParser;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;


public class PY4JCSTReadParser extends PY4JParser implements PythonParser.Reader {

    public PY4JCSTReadParser() {
        PARSER_SCRIPT_NAME = "python_cst_reader.py";
        gateway = new ReaderGateway();
    }

    @Override
    public Node.Op parse(Path path, EntityFactory entityFactory) {
        LOGGER.log(Level.INFO, "start parsing {0}", path);
        ReaderGateway readerGateway = (ReaderGateway) gateway;
        readerGateway.reset(path, entityFactory);

        /*
         * https://docs.python.org/3/using/cmdline.html
         *  -B prevents __pycache__ folders
         */
        ProcessBuilder parsePython = new ProcessBuilder("python", "-B", pythonScript, path.toString());
        Process process = null;

        try {
            long tm = System.nanoTime();
            process = parsePython.start();

            if (process.waitFor(MAX_SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    LOGGER.log(Level.INFO, "Parsing (read) successful (exit-code: 0); created {0} nodes in {1}ms",
                            new Object[]{readerGateway.getNodesCount(),
                                    String.valueOf((System.nanoTime() - tm) / 1000000)});
                    return readerGateway.getRoot();
                } else {
                    LOGGER.severe("Parce exited with code " + exitCode + "!");
                }
            } else {
                LOGGER.severe("parsing process timed out after " + MAX_SCRIPT_TIMEOUT_SECONDS + " seconds");
            }

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

        } finally {
            if (process != null) process.destroy();
        }

        return null;
    }
}
