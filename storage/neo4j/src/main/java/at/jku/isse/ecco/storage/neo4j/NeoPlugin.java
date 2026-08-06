package at.jku.isse.ecco.storage.neo4j;

import at.jku.isse.ecco.storage.StoragePlugin;
import com.google.inject.Module;

public class NeoPlugin extends StoragePlugin {

    public static String PLUGIN_ID = "at.jku.isse.ecco.storage.neo4j";
    private NeoModule module = new NeoModule();

    public NeoPlugin() {
    }


    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return NeoPlugin.class.getSimpleName();
    }

    @Override
    public String getDescription() {
        return "This is a Neo4J backend for ECCO.";
    }

}
