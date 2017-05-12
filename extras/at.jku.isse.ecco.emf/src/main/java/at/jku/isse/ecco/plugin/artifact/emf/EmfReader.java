package at.jku.isse.ecco.plugin.artifact.emf;

import static com.google.common.base.Preconditions.checkNotNull;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.nio.file.Path;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLParserPoolImpl;

/**
 * Created by hhoyos on 12/05/2017.
 */
public class EmfReader implements ArtifactReader<URI, Set<Node>> {

    private final EntityFactory entityFactory;
    private List<String> typeHierarchy = new ArrayList<String>();

    public ResourceSet getResourceSet() {
        return resourceSet;
    }

    private final ResourceSet resourceSet;
    private Map<Object, Object> loadOptions;

    @Inject
    public EmfReader(EntityFactory entityFactory) {
        this(entityFactory, new ResourceSetImpl());
    }

    @Inject
    public EmfReader(EntityFactory entityFactory, ResourceSet resourceSet) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
        this.resourceSet = resourceSet;
        // Register Ecore because people must load their metamodels
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        // Since people have to register their metamodels, xmi will be the default model type
//        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
//                .put("xmi", new XMIResourceFactoryImpl());
    }

    /**
     * Add the specific metamodel to be used by this reader
     * @param ePackage
     * @param extension
     * @param factory
     */
    public void registerMetamodel(EPackage ePackage, String extension, Resource.Factory factory) {
        resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put(extension, factory);
        typeHierarchy.add(extension);
    }

    /**
     * Add the specific metamodel to be used by this reader. By default it assumes the metamodel supports
     * XMI models.
     * @param ePackage
     */
    public void registerMetamodel(EPackage ePackage) {
        if (!resourceSet.getPackageRegistry().containsKey(ePackage)){
            registerMetamodel(ePackage, "xmi", new XMIResourceFactoryImpl());
        }
    }

    @Override
    public String getPluginId() {
        return EmfReader.class.getName();
    }

    @Override
    public String[] getTypeHierarchy() {
        return typeHierarchy.toArray(new String[0]);
    }

    @Override
    public boolean canRead(URI input) {
        // Assumes a resolved path with the file scheme
        // How can we accept other formats? test if there is a scheme, if not try another type of URI?
        // Or better make the read methods use an emf URI so we don't have to guess
        //Resource umlResource = resourceSet.createResource(input);
        try {
            Resource umlResource = resourceSet.getResource(input, true);
            return umlResource != null;
        } catch (RuntimeException ex) {
            // No factory or maldormed URI
            return false;
        }
    }

    @Override
    public Set<Node> read(URI base, URI[] input) {
        Set<Node> nodes = new HashSet<>();
        for (URI uri : input) {
            URI fullUri =  base.resolve(uri);
            if (canRead(fullUri)) {
                // PLUGIN NODE
                // TODO The defaul Path mechanism used by ECCO is not compatible with resources in remote locations
                Artifact.Op<PluginArtifactData> pluginArtifact =
                        this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), Paths.get(fullUri.toFileString())));
                Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
                nodes.add(pluginNode);
                Resource resource = resourceSet.getResource(fullUri, false);
                ((ResourceImpl) resource).setIntrinsicIDToEObjectMap(new HashMap<>());
                try {
                    resource.load(getLoadOptions());
                } catch (IOException e) {
                    throw new EccoException("Error loading model from path: " + fullUri, e);
                }
                for (EObject eObject : resource.getContents()) {
                    EObjectArtifactData eObjectArtifactData = new EObjectArtifactData(eObject);
                    Node.Op fileNode = this.entityFactory.createNode(this.entityFactory.createArtifact(eObjectArtifactData));
                    pluginNode.addChild(fileNode);
                    addChildNodes(eObjectArtifactData);
                }
            }
        }
        return nodes;
    }

    /**
     * Add a child node for each EAttribute and for each containment EReference.
     * Non containment EReferences are added as?
     * @param parent
     */
    private void addChildNodes(EObjectArtifactData parent) {
        // TODO we need to use reflective EMF... is there someway we can avoid this?
        // E.g. Can we use guice to inject the generated classes if they exist?
        EClass eClass = parent.getEObject().eClass();
        for (EAttribute attr : eClass.getEAllAttributes()) {
            System.out.println(attr.toString() + ":" + parent.getEObject().eGet(attr));
        }
        for (EReference ref : eClass.getEAllContainments()) {

        }

    }

    private Map<?,?> getLoadOptions() {
        if (loadOptions == null)
        {
            loadOptions = new HashMap<Object, Object>();
            loadOptions.put(XMIResource.OPTION_DEFER_IDREF_RESOLUTION, Boolean.TRUE);
            loadOptions.put(XMIResource.OPTION_USE_PARSER_POOL, new XMLParserPoolImpl());
        }
        return loadOptions;
    }

    @Override
    public Set<Node> read(URI[] input) {
        return this.read(URI.createURI(""), input);
    }

    @Override
    public void addListener(ReadListener listener) {

    }

    @Override
    public void removeListener(ReadListener listener) {

    }
}
