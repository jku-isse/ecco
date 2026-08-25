package at.jku.isse.ecco.adapter.python.parse.py4j;

import py4j.GatewayServer;
import py4j.GatewayServerListener;

public abstract class Gateway
{
    protected GatewayServer server;

    public void start() {
        server.start();
    }

    public void shutdown() {
        server.shutdown();
    }

    public void addListener(GatewayServerListener l) {
        server.addListener(l);
    }

    public void removeListener(GatewayServerListener l) {
        server.removeListener(l);
    }

}
