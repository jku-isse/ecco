package at.jku.isse.ecco.plugin.emf;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.emf.data.EmfResourceData;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMIResource;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by hhoyos on 19/05/2017.
 */
public class EmfWriter implements ArtifactWriter<Set<Node>, Path> {

    private final ResourceSet resourceSet;

    @Inject
    public EmfWriter(ResourceSet resourceSet) {
        this.resourceSet = resourceSet;
    }


    @Override
    public String getPluginId() {
        return EmfPlugin.class.getName();
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        EmfReconstruct reconstruct = new EmfReconstruct();
        Map<Object, Object> saveOptions = new HashMap<>();
        saveOptions.put(XMIResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
        List<Path> output = new ArrayList();
//        for (Node pluginNode : input) {
//            Artifact<PluginArtifactData> pluginArtifact = (Artifact<PluginArtifactData>) pluginNode.getArtifact();
//            Path resourcePath = base.resolve(pluginArtifact.getData().getPath());
//            output.add(resourcePath);
//            // Get the resource data
//            for (Node resourceNode : pluginNode.getChildren()) {
//                Resource resource = reconstruct.reconstructResource((Node.Op) resourceNode, resourceSet);
//                resource.setURI(URI.createFileURI(resourcePath.toString()));
//                try {
//                    resource.save(saveOptions);
//                } catch (IOException e) {
//                    throw new EccoException(e);
//                }
//            }
//        }
        return output.toArray(new Path[0]);
    }

    @Override
    public Path[] write(Set<Node> input) {
        return this.write(Paths.get("."), input);
    }

    @Override
    public void addListener(WriteListener listener) {

    }

    @Override
    public void removeListener(WriteListener listener) {

    }
}
