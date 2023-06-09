package at.jku.isse.ecco.cli.command.adapters;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class TestAdapter extends ArtifactPlugin {
    private final int id;

    public TestAdapter(int id) {
        this.id = id;
    }

    @Override
    public String getPluginId() {
        return String.format("plugin %d", id);
    }

    @Override
    public Module getModule() {
        return null;
    }

    @Override
    public String getName() {
        return String.format("name %d", id);
    }

    @Override
    public String getDescription() {
        return String.format("description %d", id);
    }
}
