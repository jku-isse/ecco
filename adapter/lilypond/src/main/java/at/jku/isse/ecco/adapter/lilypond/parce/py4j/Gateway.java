package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import py4j.GatewayServer;
import py4j.GatewayServerListener;

public class Gateway {
    private static Gateway instance;
    private final GatewayServer server;
    private final EntryPoint entrypoint;

    private Gateway() {
        entrypoint = new EntryPoint();
        // port 0: any free port - the fixed default made concurrent ECCO processes fail ("Address already in use")
        server = new GatewayServer(entrypoint, 0);
    }

    public static Gateway getInstance() {
        if (instance == null) {
            instance = new Gateway();
        }
        return instance;
    }

    public void reset() {
        if (instance != null) {
            instance.entrypoint.reset();
        }
    }

    public LilypondNode<ParceToken> getRoot() {
        if (instance == null) return null;

        return instance.entrypoint.getRoot();
    }

    public int getNodesCount() {
        return instance.entrypoint.getNodesCount();
    }

    public int getMaxDepth() {
        return instance.entrypoint.getMaxDepth();
    }

    /**
     * Start gateway server to accept connections.
     */
    public void start() {
        server.start();
    }

    /** The port the server listens on, handed to the parser script. */
    public int getPort() {
        return server.getListeningPort();
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
