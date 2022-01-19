package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondParser;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileParser implements LilypondParser<ParceToken> {
    public static final int MAX_SCRIPT_TIMEOUT = 10;
    protected static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());
    protected static GatewayServerListener gatewayListener = getGatewayListener();
    private String pythonScript;

    public void init() throws IOException {
        Gateway.getInstance().addListener(gatewayListener);
        Gateway.getInstance().start();

        try {
            URI scriptUri = Objects.requireNonNull(ClassLoader.getSystemResource("LilypondParser.py")).toURI();
            pythonScript = Paths.get(scriptUri).toString();

        } catch (NullPointerException | URISyntaxException e) {
            throw new IOException("could not load parser script", e);
        }
    }

    public LilypondNode<ParceToken> parse(Path path) {
        return parse(path, null);
    }

    public LilypondNode<ParceToken> parse(Path path, HashMap<String, Integer> tokenMetric) {
        LOGGER.log(Level.INFO, "start parsing {0}", path);
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
            if (process.waitFor(MAX_SCRIPT_TIMEOUT, TimeUnit.SECONDS)) {
                exitCode = process.exitValue();
            } else {
                LOGGER.severe("parsing process timed out after " + MAX_SCRIPT_TIMEOUT + " seconds");
            }

            if (exitCode == 0) {
                LOGGER.log(Level.FINE, "Parce exited normal, code: {0}, {1}ms", new Object[] { exitCode, (System.nanoTime() - tm) / 1000000 });
                return convertEventsToNodes(Gateway.getInstance().getBuffer(), tokenMetric);

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

    /**
     * Converts the token stream from Parce to a list of nodes.
     *
     * @param buffer Buffer with Parce events.
     * @return First node of list.
     */
    private LilypondNode<ParceToken> convertEventsToNodes(ConcurrentLinkedQueue<ParseEvent> buffer, HashMap<String, Integer> tokenMetric) {
        assert buffer != null;

        LOGGER.log(Level.INFO, "convert {0} events to tree", buffer.size());
        int depth = 0, maxDepth = 0, cnt = 0;
        ParseEvent e = buffer.poll();
        LilypondNode<ParceToken> head = new LilypondNode<>("HEAD", null);
        head.setLevel(depth);
        LilypondNode<ParceToken> n = head;
        while (e != null) {
            int pop = e.getPopContext();
            while (pop < 0) {
                pop++;
                depth--;

                LOGGER.log(Level.FINER, "closed context (depth: {0})", depth);
            }

            for (String s : e.getContexts()) {
                n = n.append(s, null, depth++);
                cnt++;
                LOGGER.log(Level.FINER, "opened {0}", n.getName());

                maxDepth = Math.max(depth, maxDepth);
            }

            for (ParceToken t : e.getTokens()) {
                if (null != tokenMetric) {
                    tokenMetric.put(t.getAction(), tokenMetric.getOrDefault(t.getAction(), 0) + 1);
                }

                n = n.append(t.getAction(), t, depth);
                cnt++;
                LOGGER.log(Level.FINER, "added token {0} \"{1}\" (depth: {2})",
                        new Object[] { t.getAction(), t.getText(), depth });
            }

            e = buffer.poll();
        }

        LOGGER.log(Level.INFO, "done conversion to {0} nodes (maxdepth: {1})", new Object[] { cnt, maxDepth });

        return head.getNext();
    }

    public void shutdown() {
        Gateway.getInstance().shutdown();
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
