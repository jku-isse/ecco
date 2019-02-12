package at.jku.isse.ecco.storage.neo4j;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

import java.nio.file.Path;

public class NeoSessionFactory {

    private final Path databasePath;
    private final SessionFactory factory;

    public NeoSessionFactory(Path databasePath){
        this.databasePath = databasePath;

        Configuration configuration = new Configuration.Builder()
                .uri(this.databasePath.toUri().toString())
                .build();

        final String[] packages = new String[] {
                "at.jku.isse.ecco.storage.neo4j.domain",
        };

        factory = new SessionFactory(configuration, packages);
    }

    public SessionFactory getFactory() {
        return factory;
    }

    public Session getNeoSession() {
        return factory.openSession();
    }
}
