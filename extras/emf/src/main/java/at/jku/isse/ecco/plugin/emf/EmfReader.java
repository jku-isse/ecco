package at.jku.isse.ecco.plugin.emf;

import static com.google.common.base.Preconditions.checkNotNull;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.emf.data.*;
import at.jku.isse.ecco.plugin.emf.util.EmfPluginUtils;
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
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
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
        return EmfPlugin.class.getName();
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
                URI fullUri = URI.createFileURI(resolvedPath.toString());
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
                String ext = name.substring(name.lastIndexOf('.') + 1);
                String factoryClass = resourceSet.getResourceFactoryRegistry()
                        .getExtensionToFactoryMap()
                        .get(ext)
                        .getClass()
                        .getCanonicalName();
                EmfResourceData resourceData = new EmfResourceData(ext, factoryClass);
                Artifact.Op<EmfResourceData> resourceArtifact =
                        this.entityFactory.createArtifact(resourceData);
                Node.Op resourceNode = this.entityFactory.createNode(resourceArtifact);
                pluginNode.addChild(resourceNode);
                TreeIterator<EObject> it = resource.getAllContents();
                // Since we have the nodeMapping and tree is no longer deep, perhaps we can do it in one pass
                while (it.hasNext()) {
                    Node.Op eObjectNode = createEObjectNode(it.next(), resourceData);
                    resourceNode.addChild(eObjectNode);
                }
                // TODO how are cross-resource references handled
                // FIXME, perhaps use a ECrossReferenceAdapter if we want to be faster
                it = resource.getAllContents();
                EObject eObject;
                while (it.hasNext()) {
                    eObject = it.next();
                    Node.Op eObjectNode = nodeMapping.get(eObject);
                    addEReferenceNodes(eObjectNode, eObject);
                }

                //EcoreUtil.UsageCrossReferencer crossRef = new EcoreUtil.UsageCrossReferencer(resource);
//                it = resource.getAllContents();
//                while (it.hasNext()) {
//                    EObject eObject = it.next();
//                    // These are all non-containment
//                    Collection<EStructuralFeature.Setting> uses = EcoreUtil.UsageCrossReferencer.find(eObject, resource);
//                    Node.Op targetNode = nodeMapping.get(eObject);
//                    for (EStructuralFeature.Setting setting : uses) {
//                        if (setting.getEStructuralFeature() instanceof EReference) {
//                            EReference ref = (EReference) setting.getEStructuralFeature();
//                            Node.Op sourceNode = nodeMapping.get(setting.getEObject());
//                            EObjectArtifactData targetData = (EObjectArtifactData) targetNode.getArtifact().getData();
//                            Node.Op refNode;
//                            if (ref.isMany()) {
//                                EList<EObject> vals = (EList<EObject>) setting.get(true);
//                                EmfArtifactData emfArtifactData = new NonContainmentReferenceData(eObject,
//                                        ref, vals, targetData);
//                                refNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
//                            } else {
//                                EmfArtifactData emfArtifactData = new NonContainmentReferenceData(eObject,
//                                        ref, null, targetData);
//                                refNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
//                            }
//                            sourceNode.addChild(refNode);
//                        }
//                    }
//                }
            }
        }
        return nodes;
    }

    /**
     * Creates the ArtifactData for the EObject and adds all its child nodes.
     * @param eObject the EObject to traverse
     * @param resourceData The ResourceData to capture the used package information
     * @return The Node that represents the EObject
     */
    private Node.Op createEObjectNode(EObject eObject, EmfResourceData resourceData) {
        EObjectArtifactData emfArtifactData = new EObjectArtifactData(eObject);
        EClass eClass = eObject.eClass();
        Resource ePackageResource = resourceSet.getResource(URI.createURI(emfArtifactData.getePackageUri()), false);
        resourceData.addEPackageInformation(eClass.getEPackage(), ePackageResource);
        Node.Op eObjectNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
        nodeMapping.put(eObject, eObjectNode);
        addContainerNode(eObject, eObjectNode, resourceData);
        addEAttributeNodes(eObjectNode, eObject);
        return eObjectNode;
    }

    private void addContainerNode(EObject eObject, Node.Op eObjectNode, EmfResourceData resourceData) {
        if (eObject.eContainer() == null) {
            ResourceContainerData eContainerArtifact = new ResourceContainerData(resourceData);
            Node.Op eContainerNode = this.entityFactory.createNode(this.entityFactory.createArtifact(eContainerArtifact));
            eObjectNode.addChild(eContainerNode);
        }
        else {
            EStructuralFeature ref = eObject.eContainingFeature();
            EObject owner = eObject.eContainer();
            assert nodeMapping.containsKey(owner);
            EObjectArtifactData ownerData = (EObjectArtifactData) nodeMapping.get(owner).getArtifact().getData();
            Node.Op refNode;
            if (ref.isMany()) {
                EList<EObject> vals = (EList<EObject>) owner.eGet(ref);
                EmfArtifactData containerData = new EContainerData(eObject, ownerData, ref, vals);
                refNode = this.entityFactory.createNode(this.entityFactory.createArtifact(containerData));
            } else {
                EmfArtifactData containerData = new EContainerData(eObject, ownerData, ref, null);
                refNode = this.entityFactory.createNode(this.entityFactory.createArtifact(containerData));
            }
            eObjectNode.addChild(refNode);
        }
    }

    /**
     * Add a child node for each EAttribute.
     * Derived and volatile features are ignored. Derived because with dynamic EMF it is very difficult to calculate
     * a derived value.
     * Volatile because these are not persisted and hence should not be persisted in Ecco either.
     * @param parentNode The ECCO parent node
     * @param eObject   The EObject to get the child nodes from
     */
    private void addEAttributeNodes(Node.Op parentNode, EObject eObject) {
        EClass eClass = eObject.eClass();
        for (EAttribute attr : eClass.getEAllAttributes()) {
            if (!attr.isVolatile()) {       // Derived attributes should be volatile too
                Object value = eObject.eGet(attr);
                Node.Op attrNode;
                if (attr.isMany()) {
                    EList vals = (EList) value;
                    List<EDataTypeArtifactData> subnodes = new ArrayList<>();
                    for (Object v : vals) {
                        EDataTypeArtifactData emfArtifactData = new EDataTypeArtifactData(v, attr, vals);
                        subnodes.add(emfArtifactData);
                    }
                    MultivalueAttributteData attrData = new MultivalueAttributteData(attr, eObject.eIsSet(attr),
                            subnodes, attr.isUnique());
                    attrNode = this.entityFactory.createNode(this.entityFactory.createArtifact(attrData));
                    for (EmfArtifactData sn : subnodes) {
                        Node.Op subNode = this.entityFactory.createNode(this.entityFactory.createArtifact(sn));
                        attrNode.addChild(subNode);
                    }
                } else {
                    EDataTypeArtifactData emfArtifactData = new EDataTypeArtifactData(value, attr, null);
                    SinglevalueAttributeData attrData = new SinglevalueAttributeData(attr, eObject.eIsSet(attr),
                            emfArtifactData, attr.isUnique());
                    attrNode = this.entityFactory.createNode(this.entityFactory.createArtifact(attrData));
                    Node.Op dataNode = this.entityFactory.createNode(this.entityFactory.createArtifact(emfArtifactData));
                    attrNode.addChild(dataNode);
                }
                parentNode.addChild(attrNode);
            }
        }
    }

    private void addEReferenceNodes(Node.Op parentNode, EObject eObject) {
        EClass eClass = eObject.eClass();
        for (EReference ref : eClass.getEAllReferences()) {
            if (!ref.isVolatile()) {       // Derived attributes should be volatile too
                Object value = eObject.eGet(ref);
                Node.Op attrNode;
                if (ref.isMany()) {
                    EList vals = (EList) value;
                    List<EObjectArtifactData> subnodes = new ArrayList<>();
                    for (Object v : vals) {
                        EObjectArtifactData refArtifactData = (EObjectArtifactData) nodeMapping.get(v)
                                .getArtifact().getData();
                        subnodes.add(refArtifactData);
                    }
                    MultivalueReferenceData attrData = new MultivalueReferenceData(ref, eObject.eIsSet(ref),
                            subnodes, ref.isUnique());
                    attrNode = this.entityFactory.createNode(this.entityFactory.createArtifact(attrData));
                } else {
                    EObjectArtifactData refArtifactData = (EObjectArtifactData) nodeMapping.get(value)
                            .getArtifact().getData();
                    SinglevalueReferenceData attrData = new SinglevalueReferenceData(ref, eObject.eIsSet(ref),
                            refArtifactData, ref.isUnique());
                    attrNode = this.entityFactory.createNode(this.entityFactory.createArtifact(attrData));
                }
                parentNode.addChild(attrNode);
            }
        }
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
