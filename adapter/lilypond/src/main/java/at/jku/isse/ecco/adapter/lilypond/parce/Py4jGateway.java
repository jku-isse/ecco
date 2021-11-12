package at.jku.isse.ecco.adapter.lilypond.parce;

import py4j.GatewayServer;
import py4j.GatewayServerListener;

import java.util.concurrent.ConcurrentLinkedQueue;

public class Py4jGateway {
    private static Py4jGateway instance;
    private final GatewayServer server;
    private final Py4jEntryPoint entrypoint;

    private Py4jGateway() {
        entrypoint = new Py4jEntryPoint();
        server = new GatewayServer(entrypoint);
    }

    public static Py4jGateway getInstance() {
        if (instance == null) {
            instance = new Py4jGateway();
        }
        return instance;
    }

    public ConcurrentLinkedQueue<Py4jParseEvent> getBuffer() {
        if (instance == null) return null;

        return instance.entrypoint.getBuffer();
    }

    /**
     * Start gateway server to accept connections.
     */
    public void start() {
        server.start();
    }

    public void shutdown() {
        server.shutdown();
        instance = null;
    }

    public void addListener(GatewayServerListener l) {
        server.addListener(l);
    }

    public void removeListener(GatewayServerListener l) {
        server.removeListener(l);
    }
}
