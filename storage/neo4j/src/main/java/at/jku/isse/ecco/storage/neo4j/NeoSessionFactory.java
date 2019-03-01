package at.jku.isse.ecco.storage.neo4j;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.kernel.configuration.Connector;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.drivers.embedded.driver.EmbeddedDriver;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

import java.nio.file.Path;

public class NeoSessionFactory {

    public static final String _BOLT_CONNECTION_STRING = "ecco";
    private final Path databasePath;
    private final SessionFactory factory;

    // https://stackoverflow.com/questions/38077512/access-the-neo4j-browser-while-running-an-embedded-connection-with-a-bolt-connec?rq=1
    public NeoSessionFactory(Path databasePath){
        this.databasePath = databasePath;

        // create embedded graph database
        BoltConnector boltConnector = new BoltConnector(_BOLT_CONNECTION_STRING);

        GraphDatabaseService graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(databasePath.toFile())
                .setConfig(boltConnector.type, "BOLT" )
                .setConfig(boltConnector.enabled, "true" )
                .setConfig(boltConnector.listen_address, "localhost:7687" )
                .setConfig(GraphDatabaseSettings.auth_enabled, "FALSE")
                .newGraphDatabase();

        registerShutdownHook(graphDb);

        // connect OGM session factory to embedded database
        EmbeddedDriver driver = new EmbeddedDriver(graphDb);
        final String[] packages = new String[] {
                "at.jku.isse.ecco.storage.neo4j.domain",
        };

        factory = new SessionFactory(driver, packages);
    }

    public SessionFactory getFactory() {
        return factory;
    }

    public Session getNeoSession() {
        return factory.openSession();
    }

    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> graphDb.shutdown()));
    }
}
