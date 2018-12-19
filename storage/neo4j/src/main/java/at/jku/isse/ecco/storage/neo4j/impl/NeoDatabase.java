package at.jku.isse.ecco.storage.neo4j.impl;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;

import java.io.File;

public class NeoDatabase {

    GraphDatabaseService neoDb;
    private File databasePath;

    public NeoDatabase(File databasePath){
        this.databasePath = databasePath;

        //https://www.baeldung.com/java-neo4j
        neoDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(databasePath)
                .setConfig(GraphDatabaseSettings.pagecache_memory, "512M" )
                .setConfig(GraphDatabaseSettings.string_block_size, "60" )
                .setConfig(GraphDatabaseSettings.array_block_size, "300" )
                .newGraphDatabase();
        registerShutdownHook(neoDb);
    }

    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }

    public File getDatabasePath() {
        return databasePath;
    }
}
