package at.jku.isse.ecco.adapter.rust;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class RustPlugin extends ArtifactPlugin {
    public static final String DESCRIPTION = "Rust Artifact Plugin";

    private RustModule module = new RustModule();

    @Override
    public String getPluginId() {
        return RustPlugin.class.getName();
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return "RustArtifactPlugin";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
}