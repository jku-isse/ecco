package at.jku.isse.ecco.adapter.lilypond.parce;

import py4j.GatewayServer;
import py4j.GatewayServerListener;

import java.util.concurrent.ConcurrentLinkedQueue;

public class LilypondParserGateway {
    private static LilypondParserGateway instance;
    private final GatewayServer server;
    private final LilypondParserEntryPoint entrypoint;

    private LilypondParserGateway() {
        entrypoint = new LilypondParserEntryPoint();
        server = new GatewayServer(entrypoint);
    }

    public static LilypondParserGateway getInstance() {
        if (instance == null) {
            instance = new LilypondParserGateway();
        }
        return instance;
    }

    public ConcurrentLinkedQueue<LilypondParserEvent> getBuffer() {
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
