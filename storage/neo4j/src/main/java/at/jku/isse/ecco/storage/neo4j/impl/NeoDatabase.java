package at.jku.isse.ecco.storage.neo4j.impl;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;

public class NeoDatabase {

    private static NeoDatabase instance;
    GraphDatabaseFactory neoDb;

    private NeoDatabase(){
        neoDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder( this.getRepoPath().toFile() )
                .setConfig(GraphDatabaseSettings.pagecache_memory, "512M" )
                .setConfig( GraphDatabaseSettings.string_block_size, "60" )
                .setConfig( GraphDatabaseSettings.array_block_size, "300" )
                .newGraphDatabase();
        registerShutdownHook(neoDb);
    }

    public static synchronized NeoDatabase getInstance(){
        if(instance == null){
            instance = new NeoDatabase();
        }
        return instance;
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
}
