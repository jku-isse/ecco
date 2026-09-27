package at.jku.isse.ecco.adapter.python.parse.py4j.cst.reader;

import at.jku.isse.ecco.adapter.python.parse.py4j.Gateway;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import py4j.GatewayServer;

import java.nio.file.Path;

public class ReaderGateway extends Gateway {

    private final ReaderEntryPoint entrypoint;
    ReaderGateway() {
        entrypoint = new ReaderEntryPoint();
        // port 0: any free port - the fixed default made concurrent ECCO processes fail ("Address already in use")
        server = new GatewayServer(entrypoint, 0);
    }

    public void reset(Path path, EntityFactory entityFactory){
        entrypoint.reset(path, entityFactory);
    }

    public Node.Op getRoot() {
        return entrypoint.getRoot();
    }

    public int getNodesCount() {
        return entrypoint.getNodesCount();
    }
}
