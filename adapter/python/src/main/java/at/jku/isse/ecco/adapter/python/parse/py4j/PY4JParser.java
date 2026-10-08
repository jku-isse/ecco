package at.jku.isse.ecco.adapter.python.parse.py4j;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.PythonFinder;
import at.jku.isse.ecco.adapter.python.PythonParser;
import at.jku.isse.ecco.adapter.python.PythonPlugin;
import at.jku.isse.ecco.service.AdapterPreferences;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;

import java.util.List;
import java.util.ArrayList;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class PY4JParser implements PythonParser {

    public final int MAX_SCRIPT_TIMEOUT_SECONDS = 10;
    public static final Logger LOGGER = Logger.getLogger(PythonPlugin.class.getName());
    public final GatewayServerListener gatewayListener = getGatewayListener();
    public String pythonScript;
    protected String PARSER_SCRIPT_NAME;
    protected Gateway gateway;


    @Override
    public void init() throws IOException {
        gateway.addListener(gatewayListener);
        gateway.start();

        // a copy of its own: a shared one in the temp directory was rewritten by every parser, also
        // while another process's python was reading it
        Path file = Files.createTempFile("ecco-" + PARSER_SCRIPT_NAME.replace(".py", "-"), ".py");
        pythonScript = file.toString();

        {
            try (InputStream is = ClassLoader.getSystemResourceAsStream(PARSER_SCRIPT_NAME);
                 OutputStream os = Files.newOutputStream(file, StandardOpenOption.TRUNCATE_EXISTING)) {

                if (is != null) {
                    os.write(is.readAllBytes());
                } else {
                    throw new IOException("no resource '" + PARSER_SCRIPT_NAME + "' found");
                }

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "could not initialize parser", e);
                shutdown();
                throw e;
            }
        }
    }

    protected void logOutput(Process process) throws IOException {
        final BufferedReader parceStdRd = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
        parceStdRd.lines().iterator().forEachRemaining(sj::add);
        parceStdRd.close();
        process.getInputStream().close();

        if (sj.length() > 0) {
            LOGGER.info("** Output **");
            LOGGER.info(sj.toString());
            LOGGER.info("** END - Output **");
        }
    }

    protected String getStackTrace(Process process) {
        final BufferedReader parceErrRd = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringJoiner sjErr = new StringJoiner(System.getProperty("line.separator"));
        parceErrRd.lines().iterator().forEachRemaining(sjErr::add);
        return sjErr.toString();
    }

    public void shutdown() {
        gateway.shutdown();
        gateway.removeListener(gatewayListener);
        if (pythonScript != null) {
            try {
                Files.deleteIfExists(Path.of(pythonScript));
            } catch (IOException ignored) {
                // a leftover temporary file
            }
        }
    }

    /** Name of the environment variable that tells the script the port of this parser's gateway. */
    public static final String PORT_VARIABLE = "ECCO_PY4J_PORT";

    /** What the scripts import. */
    static final List<String> MODULES = List.of("libcst", "py4j");

    /**
     * The python the scripts run with: the one set in Preferences, else the first of python, python3
     * and the usual install locations that imports libcst and py4j (see {@link PythonFinder}).
     *
     * @throws EccoException naming every python tried and what it lacks, when none will do
     */
    public static String python() {
        return PythonFinder.find(AdapterPreferences.getPythonAdapterPython(), MODULES, null,
                "reads and writes Python files", "Install what is missing into one of them"
                        + " (python -m pip install libcst py4j), or set the Python adapter's Python under Preferences > Plugins.");
    }

    /** {@code python -B <script> <arguments>} with {@link #python()}, told the port of this parser's gateway. */
    protected ProcessBuilder pythonProcess(String... arguments) {
        String python = python();
        List<String> command = new ArrayList<>(List.of(python, "-B", pythonScript));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put(PORT_VARIABLE, String.valueOf(gateway.getPort()));
        return builder;
    }

    private static GatewayServerListener getGatewayListener() {
        return new GatewayServerListener() {
            @Override
            public void connectionError(Exception e) {
                LOGGER.log(Level.SEVERE, "gateway connection error", e);
            }

            @Override
            public void connectionStarted(Py4JServerConnection py4JServerConnection) {
                LOGGER.fine("gateway connection started");
            }

            @Override
            public void connectionStopped(Py4JServerConnection py4JServerConnection) {
                LOGGER.fine("gateway connection stopped");
            }

            @Override
            public void serverError(Exception e) {
                LOGGER.log(Level.SEVERE, "gateway server error", e);
            }

            @Override
            public void serverPostShutdown() {
                LOGGER.fine("gateway shutdown");
            }

            @Override
            public void serverPreShutdown() {
                LOGGER.fine("gateway before shutdown");
            }

            @Override
            public void serverStarted() {
                LOGGER.fine("gateway started");
            }

            @Override
            public void serverStopped() {
                LOGGER.fine("gateway stopped");
            }
        };
    }
}
