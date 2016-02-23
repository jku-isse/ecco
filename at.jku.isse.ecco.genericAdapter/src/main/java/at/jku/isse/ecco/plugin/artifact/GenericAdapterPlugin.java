package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.GenericAdapterModule;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder.GenericAdapterReader;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.printer.GenericAdapterWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Module;

import java.nio.file.Path;
import java.util.Set;

/**
 * @author Michael Jahn
 */
public class GenericAdapterPlugin extends ArtifactPlugin {

    private GenericAdapterModule module = new GenericAdapterModule();

    @Override
    public String getPluginId() {
        return GenericAdapterPlugin.class.getName();
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return "Generic Adapter Plugin";
    }

    @Override
    public String getDescription() {
        return "A generic adapter plugin";
    }

    @Override
    public ArtifactReader<Path, Set<Node>> createReader(EntityFactory entityFactory) {
        return new GenericAdapterReader(entityFactory);
    }

    @Override
    public ArtifactWriter<Set<Node>, Path> createWriter() {
        return new GenericAdapterWriter();
    }
}
