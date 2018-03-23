package at.jku.isse.ecco.storage.json;

import at.jku.isse.ecco.storage.StoragePlugin;
import com.google.inject.Module;

public class JsonPlugin extends StoragePlugin {

    public static String PLUGIN_ID = "at.jku.isse.ecco.storage.json";
    private JsonModule module = new JsonModule();

    public JsonPlugin() {
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
