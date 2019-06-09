package at.jku.isse.ecco.storage.xml;

import at.jku.isse.ecco.storage.StoragePlugin;
import com.google.inject.Module;

public class XmlPlugin extends StoragePlugin {

    public static String PLUGIN_ID = "at.jku.isse.ecco.storage.xml";
    private XmlModule module = new XmlModule();

    public XmlPlugin() {
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
        return XmlPlugin.class.getSimpleName();
    }

    @Override
    public String getDescription() {
        return "This is a JSON backend for ECCO.";
    }

}
