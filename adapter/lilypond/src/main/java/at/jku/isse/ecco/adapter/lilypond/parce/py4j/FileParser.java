package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.EccoException;
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

        // a copy of its own: a shared one in the temp directory was only written if missing, so an
        // outdated copy from an older version kept being used
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

    public LilypondNode<ParceToken> parse(Path path) {
        return parse(path, null);
    }

    public LilypondNode<ParceToken> parse(Path path, HashMap<String, Integer> tokenMetric) {
        LOGGER.log(Level.INFO, "start parsing {0}", path);
        Gateway.getInstance().reset();
        ProcessBuilder lilyparce = new ProcessBuilder("python", pythonScript, path.toString());
        lilyparce.environment().put("ECCO_PY4J_PORT", String.valueOf(Gateway.getInstance().getPort()));
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
                throw new EccoException("Parce exited with code " + exitCode + " for file " + path + ":\n" + sjErr);
            }

        } catch (IOException e) {
            // e.g. no python executable - fail the commit rather than committing an empty file
            // (see UnreadableFileCommitTest), as returning null here used to
            throw new EccoException("Could not run parce for file " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EccoException("Interrupted while parsing file " + path, e);
        } finally {
            if (process != null) process.destroy();
        }
    }

    public void shutdown() {
        Gateway gateway = Gateway.getInstance();
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
