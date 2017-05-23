package at.jku.isse.ecco.plugin.artifact.emf;

import static com.google.common.base.Preconditions.checkNotNull;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.artifact.emf.data.*;
import at.jku.isse.ecco.plugin.artifact.emf.util.EmfPluginUtils;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * This class can transform an EMF model into an ECCO artifact tree.
 *
 * The class expects all required metamodels, models and factories to be loaded into it's ResourceSet. If a new, empty,
 * ResourceSet is provided during instantiation the EmfReader provides a convenience method for registering metamodels
 * and and factories ({@see registerMetamodel}).
 *
 * For more advanced setups, e.g. providing custom DataType conversion delegates and factories, the EmfReader's
 * ResourseSet can be accessed ({@see getResourceSet}) to register factories and/or load additional resources.
 *
 * Guice bootstraing should provide an {@link EntityFactory} and a {@link ResourceSet} binding (typically the
 * {@link ResourceSetImpl}) to instantiate a new EmfReader.
 * EmfReader
 *
 * @author Horacio Hoyos
 * @since 1.1
 */
public class EmfReader implements ArtifactReader<Path, Set<Node.Op>> {

    private final EntityFactory entityFactory;
    private final ResourceSet resourceSet;
    private List<String> typeHierarchy = new ArrayList<String>();
    private Map<Object, Object> loadOptions;
    /**
     * When creating non-containment references we need to find the node that represents the EObject
     */
    private Map<EObject, Node.Op> nodeMapping = new HashMap<>();

    @Inject
    public EmfReader(EntityFactory entityFactory, ResourceSet resourceSet) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
        this.resourceSet = resourceSet;
        EcorePackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());

        resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
    }

    public Map<Object, Object> getLoadOptions() {
        return loadOptions;
    }

    public void setLoadOptions(Map<Object, Object> loadOptions) {
        this.loadOptions = loadOptions;
    }

    public ResourceSet getResourceSet() {
        return resourceSet;
    }

    /**
     * Add the specific metamodel to be used by this reader.
     *
     * @param ePackage The EPackage of the metamodel
     * @param extension The extension of models (files) that conform to this metamodel
     * @param factory The facory used to load models that conform to this metamodel
     */
    public void registerMetamodel(EPackage ePackage, String extension, Resource.Factory factory) {
        resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put(extension, factory);
        typeHierarchy.add(extension);
    }

    /**
     * Add the specific metamodel to be used by this reader. It assumes the metamodel supports XMI models,
     * i.e. models that conform to this metamodel can be loaded using the XMIResourceFactoryImpl.
     * @param ePackage
     */
    public void registerMetamodel(EPackage ePackage) {
        if (!resourceSet.getPackageRegistry().containsKey(ePackage)){
            registerMetamodel(ePackage, "xmi", new XMIResourceFactoryImpl());
        }
    }

    /**
     * Loads en Ecore (*.ecore) metamodel and registers it in this reader. It assumes the metamodel supports XMI models,
     * i.e. models that conform to this metamodel can be loaded using the XMIResourceFactoryImpl.
     * @param uri
     */
    public void registerMetamodel(URI uri) {
        Resource r = resourceSet.getResource(uri, true);
        EObject eObject = r.getContents().get(0);
        if (eObject instanceof EPackage) {
            EPackage p = (EPackage)eObject;
            registerMetamodel(p);
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
    public boolean canRead(Path path) {
        // Since we are dealing with local resources, we can just est for File existence
        if (!Files.exists(path)) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return false;
        }
        if (!Files.isRegularFile(path))
            return false;
        if (!Files.isReadable(path)) {
            return false;
        }
        // Check that the extension is registered.
        String name = path.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.')+1);
        return resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().containsKey(ext);
        // FIXME Bring back the URI check when we handle more than local resources
//        // FileURI from Path
//        URI input_uri = URI.createFileURI(input.toString());
//        Map<Object, Object> existsOptions = new HashMap<>();
//        existsOptions.put(ExtensibleURIConverterImpl.OPTION_TIMEOUT, 10000);    // 10s timeout
//        return resourceSet.getURIConverter().exists(input_uri, existsOptions);
    }

    /**
     * Any additional load options should be set before calling the read method ({@see getLoadOptions}).
     * @param base the base URI used to resolve the input URIs
     * @param input the array of input resources to load.
     * @return
     */
    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();
        for (Path path : input) {
            Path resolvedPath = base.resolve(path);
            if (canRead(resolvedPath)) {
                // TODO The defaul Path mechanism used by ECCO is not compatible with resources in remote locations
                URI fullUri =  URI.createFileURI(resolvedPath.toString());
                //Resource resource = resourceSet.getResource(fullUri, false);
                Resource resource = resourceSet.createResource(fullUri);
                ((ResourceImpl) resource).setIntrinsicIDToEObjectMap(new HashMap<>());
                loadDefaultLoadOptions();
                try {
                    resource.load(loadOptions);
                } catch (IOException e) {
                    throw new EccoException("Error loading model from path: " + fullUri, e);
                } catch (WrappedException ex) {
                    // Error loading the model
                    throw new EccoException("There was a problem loading model " + fullUri.toString(), ex);
                } catch (RuntimeException ex) {
                    // No factory or malformed URI
                    throw new EccoException("There was a problem loading model " + fullUri.toString(), ex);
                }

                Artifact.Op<PluginArtifactData> pluginArtifact =
                        this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
                Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
                nodes.add(pluginNode);

                // A resource node to save resource and metamodel information
                String name = path.getFileName().toString();
                String ext = name.substring(name.lastIndexOf('.')+1);
                String factoryClass = resourceSet.getResourceFactoryRegistry()
                        .getExtensionToFactoryMap()
                        .get(ext)
                        .getClass()
                        .getCanonicalName();
                EmfResourceData resourceData = new EmfResourceData(ext, factoryClass);
                Node.Op resourceNode = this.entityFactory.createNode(resourceData);
                pluginNode.addChild(resourceNode);
                for (EObject eObject : resource.getContents()) {
                    Node.Op eObjectNode = createEObjectSubtree(eObject, null, null);
                    resourceNode.addChild(eObjectNode);
                }
                // References have to be done at the end when all nodes have been created.
                // We also use the loop to capture the EPackage data, to avoid doing the loop twice
                // TODO how are cross-resource references handled
                // FIXME, perhaps use a ECrossReferenceAdapter if we want to be faster
                //EcoreUtil.UsageCrossReferencer crossRef = new EcoreUtil.UsageCrossReferencer(resource);
                TreeIterator<EObject> it = resource.getAllContents();
                while (it.hasNext()) {
                    EObject eObject = it.next();
                    EClass eClass = eObject.eClass();
                    EPackage ePackage = eClass.getEPackage();
                    Resource ePackageResource = resourceSet.getResource(URI.createURI(ePackage.getNsURI()), false);
                    resourceData.addEPackageInformation(ePackage, ePackageResource);
                    Collection<EStructuralFeature.Setting> uses = EcoreUtil.UsageCrossReferencer.find(eObject, resource);
                    Node.Op targetNode = nodeMapping.get(eObject);
                    for (EStructuralFeature.Setting ref : uses) {
                        Node.Op sourceNode = nodeMapping.get(ref.getEObject());
                        EStructuralFeature sf = ref.getEStructuralFeature();
                        EObjectArtifactData targetData = (EObjectArtifactData) targetNode.getArtifact().getData();
                        Node.Op refNode;
                        if (sf.isMany()) {
                            EList<EObject> vals = (EList<EObject>) ref.get(true);
                            EmfArtifactData emfArtifactData = new NonContainmentReferenceData(eObject,
                                    sf, vals, targetData);
                            refNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
                        }
                        else {
                            EmfArtifactData emfArtifactData = new NonContainmentReferenceData(eObject,
                                    sf, null, targetData);
                            refNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
                        }
                        sourceNode.addChild(refNode);
                    }
                }
            }
        }
        return nodes;
    }

    /**
     * Add a child node for each EAttribute and for each containment EReference.
     * Non containment EReferences are added as?
     * @param parentNode The ECCO parent node
     * @param eObject   The EObject to get the child nodes from
     */
    private void addChildNodes(Node.Op parentNode, EObject eObject) {
        EClass eClass = eObject.eClass();
        for (EAttribute attr : eClass.getEAllAttributes()) {
            Object value = eObject.eGet(attr);
            if (attr.isMany()) {
                EList vals = (EList) value;
                for (Object v : vals) {
                    EmfArtifactData emfArtifactData = new EDataTypeArtifactData(v, attr, vals);
                    Node.Op attrNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
                    parentNode.addChild(attrNode);
                }
            }
            else {
                EmfArtifactData emfArtifactData = new EDataTypeArtifactData(value, attr, null);
                Node.Op attrNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
                parentNode.addChild(attrNode);
            }
        }
        for (EReference ref : eClass.getEAllContainments()) {
            Object value = eObject.eGet(ref);
            Node.Op refNode;
            if (ref.isMany()) {
                EList<EObject> vals = (EList<EObject>) value;
                for (EObject child : vals) {
                    refNode = createEObjectSubtree(child, ref, vals);
                    parentNode.addChild(refNode);
                }
            }
            else {
                EObject child = (EObject) value;
                refNode = createEObjectSubtree(child, ref, null);
                parentNode.addChild(refNode);
            }
        }
    }

    /**
     * Creates the ArtifactData for the EObject and adds all its child nodes.
     * @param eObject the EObject to traverse
     * @param ref   the EReference that pointed to the eObject, null if in the root
     * @param container If the EReference is multivalued, then this is the EList that contains the eObject, else null
     * @return The Node that is the root of the subtree
     */
    private Node.Op createEObjectSubtree(EObject eObject, EReference ref, EList<EObject> container) {
        EmfArtifactData emfArtifactData = new EObjectArtifactData(eObject, ref, container);
        Node.Op refNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
        nodeMapping.put(eObject, refNode);
        addChildNodes(refNode, eObject);
        return refNode;
    }

    private void loadDefaultLoadOptions() {
        if (loadOptions == null)
        {
            loadOptions = new HashMap<Object, Object>();
        }
        loadOptions.putAll(EmfPluginUtils.getDefaultLoadOptions());
    }

    @Override
    public Set<Node.Op> read(Path[] input) {

        return this.read(Paths.get("."), input);
    }

    @Override
    public void addListener(ReadListener listener) {

    }

    @Override
    public void removeListener(ReadListener listener) {

    }
}
