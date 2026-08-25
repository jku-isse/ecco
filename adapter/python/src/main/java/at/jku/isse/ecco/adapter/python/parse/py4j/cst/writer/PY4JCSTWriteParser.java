package at.jku.isse.ecco.adapter.python.parse.py4j.cst.writer;


import at.jku.isse.ecco.adapter.python.PythonParser;
import at.jku.isse.ecco.adapter.python.parse.py4j.PY4JParser;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;


public class PY4JCSTWriteParser extends PY4JParser implements PythonParser.Writer {

    public PY4JCSTWriteParser() {
        PARSER_SCRIPT_NAME = "python_cst_writer.py";
        gateway = new WriterGateway();
    }

    @Override
    public void parse(Path path, Node root) {

        LOGGER.log(Level.INFO, "start parsing {0}", path);
        WriterGateway writerGateway = (WriterGateway) gateway;
        writerGateway.reset(path, root);

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
                    LOGGER.log(Level.INFO, "Parsing (write) successful (exit-code: 0); wrote 1 file in {0}ms",
                            new Object[]{String.valueOf((System.nanoTime() - tm) / 1000000)});
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
    }
}
