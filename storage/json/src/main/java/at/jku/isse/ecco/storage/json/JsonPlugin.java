package at.jku.isse.ecco.storage.json;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.storage.StoragePlugin;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.core.MemVariant;
import at.jku.isse.ecco.storage.mem.feature.MemConfiguration;
import com.google.inject.Module;

import static com.jsoniter.spi.JsoniterSpi.registerTypeImplementation;

public class JsonPlugin extends StoragePlugin {

    public static String PLUGIN_ID = "at.jku.isse.ecco.storage.json";
    private JsonModule module = new JsonModule();

    public JsonPlugin() {
        //Register all possible interfaces!
        registerTypeImplementation(Configuration.class, MemConfiguration.class);
        registerTypeImplementation(Remote.class, MemRemote.class);
        registerTypeImplementation(Variant.class, MemVariant.class);
        // http://jsoniter.com/java-features.html#wrapper--unwrapper <-- Needed for ArtifactData
        //TODO Add performance: http://jsoniter.com/java-features.html#performance-is-optional
        //TODO Maybe create a own artifact factory
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
        return JsonPlugin.class.getSimpleName();
    }

    @Override
    public String getDescription() {
        return "This is a JSON backend for ECCO.";
    }

}
