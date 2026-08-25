package at.jku.isse.ecco.adapter.python;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class PythonPlugin extends ArtifactPlugin {
    private PythonModule module = new PythonModule();

    @Override
    public String getPluginId() {
        return PythonPlugin.class.getName();
    }

    @Override
    public Module getModule() { return this.module; }

    @Override
    public String getName() {
        return "PythonArtifactPlugin";
    }

    @Override
    public String getDescription() {
        return "Python Artifact Plugin";
    }

}
