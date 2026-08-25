package at.jku.isse.ecco.adapter.python.parse.py4j;

import at.jku.isse.ecco.adapter.python.PythonParser;
import at.jku.isse.ecco.adapter.python.PythonPlugin;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;

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

    private final boolean RECREATE_SCRIPTS = true; // overwrite script file in temp-folder on run > true during development

    @Override
    public void init() throws IOException {
        gateway.addListener(gatewayListener);
        gateway.start();

        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path file = tempDir.resolve(PARSER_SCRIPT_NAME);
        pythonScript = file.toString();

        if (RECREATE_SCRIPTS || !Files.exists(file)) {
            try (InputStream is = ClassLoader.getSystemResourceAsStream(PARSER_SCRIPT_NAME);
                 OutputStream os = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

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
