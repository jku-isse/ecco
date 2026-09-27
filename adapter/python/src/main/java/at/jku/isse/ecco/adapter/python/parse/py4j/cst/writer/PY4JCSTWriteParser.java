package at.jku.isse.ecco.adapter.python.parse.py4j.cst.writer;


import at.jku.isse.ecco.adapter.python.PythonParser;
import at.jku.isse.ecco.adapter.python.parse.py4j.PY4JParser;
import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public RenderedSource render(Path path, Node root) throws IOException {
        WriterGateway writerGateway = (WriterGateway) gateway;
        writerGateway.reset(path, root);

        // the script's output (errors, e.g. a missing module) goes to a file: reading a pipe could
        // block for as long as the script runs
        Path output = Files.createTempFile("ecco-python-render", ".log");
        ProcessBuilder renderPython = new ProcessBuilder("python", "-B", pythonScript, path.toString(), "--render")
                .redirectErrorStream(true).redirectOutput(output.toFile());
        Process process = null;
        try {
            try {
                process = renderPython.start();
            } catch (IOException e) {
                throw new IOException("Showing Python code needs `python` with the modules libcst and py4j on the PATH.", e);
            }
            if (!process.waitFor(MAX_SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                throw new IOException("Rendering " + path + " timed out after " + MAX_SCRIPT_TIMEOUT_SECONDS + " seconds.");
            WriterEntryPoint entryPoint = writerGateway.getEntryPoint();
            if (process.exitValue() != 0 || entryPoint.getRenderedCode() == null)
                throw new IOException("Rendering " + path + " failed (python exit code " + process.exitValue() + "). "
                        + "Showing Python code needs `python` with the modules libcst and py4j on the PATH.\n"
                        + Files.readString(output, StandardCharsets.UTF_8).strip());

            String code = entryPoint.getRenderedCode();
            List<RenderedSource.Span> spans = new ArrayList<>();
            String reported = entryPoint.getRenderedSpans();
            if (reported != null && !reported.isEmpty()) {
                for (String span : reported.split(";")) {
                    String[] f = span.split(",");
                    int start = RenderedSource.offset(code, Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                    int end = RenderedSource.offset(code, Integer.parseInt(f[3]), Integer.parseInt(f[4]));
                    spans.add(new RenderedSource.Span(entryPoint.getNode(Integer.parseInt(f[0])), start, Math.max(start, end)));
                }
            }
            return new RenderedSource(code, spans);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Rendering " + path + " was interrupted.", e);
        } finally {
            if (process != null) process.destroy();
            Files.deleteIfExists(output);
        }
    }
}
