package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondParser;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileParser implements LilypondParser<ParceToken> {
    public static final int MAX_SCRIPT_TIMEOUT_SECONDS = 10;
    public static final String PARSER_SCRIPT_NAME = "LilypondParser_1.py";
    protected static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());
    protected static final GatewayServerListener gatewayListener = getGatewayListener();
    private String pythonScript;

    public void init() throws IOException {
        Gateway.getInstance().addListener(gatewayListener);
        Gateway.getInstance().start();

        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path file = tempDir.resolve(PARSER_SCRIPT_NAME);
        pythonScript = file.toString();

        if (!Files.exists(file)) {
            try (InputStream is = ClassLoader.getSystemResourceAsStream(PARSER_SCRIPT_NAME);
                OutputStream os = Files.newOutputStream(file, StandardOpenOption.CREATE)) {

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

    public LilypondNode<ParceToken> parse(Path path) {
        return parse(path, null);
    }

    public LilypondNode<ParceToken> parse(Path path, HashMap<String, Integer> tokenMetric) {
        LOGGER.log(Level.INFO, "start parsing {0}", path);
        Gateway.getInstance().reset();
        ProcessBuilder lilyparce = new ProcessBuilder("python", pythonScript, path.toString());
        Process process = null;
        try {
            process = lilyparce.start();
            long tm = System.nanoTime();
            final BufferedReader parceErrRd = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringJoiner sjErr = new StringJoiner(System.getProperty("line.separator"));
            parceErrRd.lines().iterator().forEachRemaining(sjErr::add);

            if (LOGGER.isLoggable(Level.FINE)) {
                final BufferedReader parceStdRd = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
                parceStdRd.lines().iterator().forEachRemaining(sj::add);

                if (sj.length() > 0) {
                    LOGGER.fine("** Output **");
                    LOGGER.fine(sj.toString());
                    LOGGER.fine("** END - Output **");
                }
            }

            int exitCode = -1;
            if (process.waitFor(MAX_SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                exitCode = process.exitValue();
            } else {
                LOGGER.severe("parsing process timed out after " + MAX_SCRIPT_TIMEOUT_SECONDS + " seconds");
            }

            if (exitCode == 0) {
                LOGGER.log(Level.FINE, "Parce exited normal, code: {0}, {1}ms", new Object[] { exitCode, (System.nanoTime() - tm) / 1000000 });
                if (tokenMetric != null) {
                    LilypondNode<ParceToken> n = Gateway.getInstance().getRoot();
                    while (n != null) {
                        if (n.getData() != null) {
                            tokenMetric.put(n.getData().getAction(),
                                    tokenMetric.getOrDefault(n.getData().getAction(), 0) + 1);
                        }
                        n = n.getNext();
                    }
                }
                LOGGER.log(Level.INFO, "created {0} nodes (maxDepth: {1})",
                        new Object[] { Gateway.getInstance().getNodesCount(),
                        Gateway.getInstance().getMaxDepth()});

                return Gateway.getInstance().getRoot();

            } else {
                LOGGER.severe("Parce exited with code " + exitCode + ":\n" + sjErr);
            }

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

        } finally {
            if (process != null) process.destroy();
        }

        return null;
    }

    public void shutdown() {
        Gateway.getInstance().shutdown();
        Gateway.getInstance().removeListener(gatewayListener);
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
