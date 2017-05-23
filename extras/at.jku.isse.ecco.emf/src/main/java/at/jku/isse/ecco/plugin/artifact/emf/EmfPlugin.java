package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import com.google.inject.Module;

/**
 * Created by hhoyos on 18/05/2017.
 */
public class EmfPlugin extends ArtifactPlugin{

    public static final String PLUGIN_ID = "Ecco EMF Plugin";
    public static final String PLUGIN_NAME = "EMF Artifact Plugin";
    public static final String PLUGIN_DESC = "Ecco Plugin to support EMF models.";

    private EmfModule module = new EmfModule();

    @Override
    public String getPluginId() {
        // return PLUGIN_ID; ?
        return EmfPlugin.class.getName();
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public String getDescription() {
        return PLUGIN_DESC;
    }
}
