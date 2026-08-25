package at.jku.isse.ecco.adapter.python.parse.py4j.cst.writer;

import at.jku.isse.ecco.adapter.python.parse.py4j.Gateway;
import at.jku.isse.ecco.tree.Node;
import py4j.GatewayServer;

import java.nio.file.Path;

public class WriterGateway extends Gateway {
    private final WriterEntryPoint entrypoint;
    WriterGateway() {
        entrypoint = new WriterEntryPoint();
        server = new GatewayServer(entrypoint);
    }

    public void reset(Path path, Node root) {
        entrypoint.reset(path, root);
    }
}
