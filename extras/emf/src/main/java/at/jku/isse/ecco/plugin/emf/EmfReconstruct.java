package at.jku.isse.ecco.plugin.emf;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.plugin.emf.data.*;
import at.jku.isse.ecco.plugin.emf.util.EmfPluginUtils;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EContentAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Given a Ecco tree, this class can construct an EMF Resource.
 *
 * TODO Do we need to handle partial models?
 * Created by hhoyos on 22/05/2017.
 */
public class EmfReconstruct {

    /**
     * When creating non-containment references we need to find the node that represents the EObject
     */
    Map<EObjectArtifactData, EObject> dataEObjectMap = new HashMap<>();

    Map<Node.Op, EObject> nodeEObjectMap = new HashMap<>();

//    public Resource reconstructResource(Node.Op resourceNode, ResourceSet resourceSet) {
//        assert resourceNode.getArtifact().getData() instanceof EmfResourceData;
//
//        checkMetamodels(resourceNode, resourceSet);
//        ArrayList<EObject> newEObjects = new ArrayList<>();
//        // Create the resource, the uri can be assigned later, e.g. the writer will assign an uri to persist.
//        String ext = ((EmfResourceData) resourceNode.getArtifact().getData()).getExtension();
//        final Resource resource = resourceSet.createResource(URI.createURI("." + ext));
//        // Add all EObjects to the root, containment will be handled later.
//        // Add a notifier to the EObject so we can remove it from the root when the container is assigned
//        EContentAdapter adapter = new EContentAdapter() {
//
//            @Override
//            public void notifyChanged(Notification notification) {
//                super.notifyChanged(notification);
//                if (notification.getEventType() == Notification.ADD) {
//                    EObject child = (EObject) notification.getNewValue();
//                    if (child.eContainer() != null) {
//                        resource.getContents().remove(child);
//                    }
//                }
//            }
//        };
//        for (Node.Op child : resourceNode.getChildren()) {
//            EObject eObject = createEObject(child, resourceSet);
//            eObject.eAdapters().add(adapter);
//            if (eObject != null) {
//                resource.getContents().add(eObject);
//            }
//        }
//        for (Node.Op child : resourceNode.getChildren()) {
//            ArtifactData data = child.getArtifact().getData();
//            if (data instanceof EObjectArtifactData) {
//                createReferences(child, resourceSet);
//            }
//        }
//        return resource;
//    }
//
//    public EObject getEObjectForNode(Node.Op node) {
//        return nodeEObjectMap.get(node);
//    }
//
//    private EObject createEObject(Node.Op eObjectNode, ResourceSet resourceSet) throws EccoException {
//        EObject eObject = null;
//        if (eObjectNode.getArtifact().getData() instanceof EObjectArtifactData) {
//            EObjectArtifactData eObjectData = (EObjectArtifactData) eObjectNode.getArtifact().getData();
//            String eClassName = eObjectData.geteClassName();
//            String ePackageUri = eObjectData.getePackageUri();
//            EPackage ePackage = resourceSet.getPackageRegistry().getEPackage(ePackageUri);
//            EClassifier eClass = ePackage.getEClassifier(eClassName);
//            assert eClass instanceof EClass;
//            eObject = ePackage.getEFactoryInstance().create((EClass) eClass);
//            //eObject = new AmbiguousEObject(ePackage.getEFactoryInstance().create((EClass) eClass));
//            eObject = AmbiguousEObject.wrap(eObject);
//            nodeEObjectMap.put(eObjectNode, eObject);
//            dataEObjectMap.put(eObjectData, eObject);
//            // Add all properties, references will be added when the tree is expanded
//            for (Node.Op child : eObjectNode.getChildren()) {
//                if (child.getArtifact().getData() instanceof EDataTypeArtifactData) {
//                    EDataTypeArtifactData dataTypeData = (EDataTypeArtifactData) child.getArtifact().getData();
//                    int sfId = dataTypeData.getFeatureId();
//                    EStructuralFeature sf = ((EClass) eClass).getEStructuralFeature(sfId);
//                    if (!sf.isUnsettable() && !sf.isDerived() && sf.isChangeable()) {
//                        Object value = getEDataTypeValue(resourceSet, ePackage, dataTypeData);
//                        assignObjectToFeatureValue(eObject, sf, value);
//                    }
//                }
//            }
//        }
//        return eObject;
//    }
//
//    /**
//     * For single-valued properties ambiguities are detected in the eSet method.
//     * Multi-valued are more tricky as we need to track changes in the list.
//     * If the feature is unordered we need to
//     *
//     * @param owner
//     * @param sf
//     * @param value
//     */
//    private void assignObjectToFeatureValue(EObject owner, EStructuralFeature sf, Object value) {
//        if (sf.isMany()) {
//            EList<Object> values = (EList<Object>) owner.eGet(sf);
//            if (values.isEmpty()) {
//
//            }
//            values.add(value);
//        } else {
////            if (owner.eIsSet(sf)) {
////                System.out.println("Feature " + sf.getName() + " is being set multiple times for " + owner);
////            }
//            owner.eSet(sf, value);
//        }
//    }
//
//    private void createReferences(Node.Op node, ResourceSet resourceSet) {
//
//        EObject nodeEObject = getEObjectForNode(node);
//        assert nodeEObject != null;
//        for (Node.Op child : node.getChildren()) {
//            ArtifactData childData = child.getArtifact().getData();
//            if (childData instanceof NonContainmentReferenceData) {
//                EObjectArtifactData nodeData = (EObjectArtifactData) node.getArtifact().getData();
//                String eClassName = nodeData.geteClassName();
//                String ePackageUri = nodeData.getePackageUri();
//                EPackage ePackage = resourceSet.getPackageRegistry().getEPackage(ePackageUri);
//                EClassifier eClass = ePackage.getEClassifier(eClassName);
//                assert eClass instanceof EClass;
//                NonContainmentReferenceData eReferenceData = (NonContainmentReferenceData) childData;
//                int sfId = eReferenceData.getFeatureId();
//                EStructuralFeature sf = ((EClass) eClass).getEStructuralFeature(sfId);
//                if (!sf.isUnsettable() && !sf.isDerived() && sf.isChangeable()) {
//                    EObjectArtifactData targetData = eReferenceData.getReference();
//                    EObject referenceEObject = dataEObjectMap.get(targetData);
//                    assert referenceEObject != null;
//                    assignObjectToFeatureValue(nodeEObject, sf, referenceEObject);
//                }
//            }
//            else if (childData instanceof EContainerData) {
//                EContainerData eObjectData = (EContainerData) childData;
//                EObjectArtifactData ownerData = eObjectData.getContainer();
//                EObject containerEObject = dataEObjectMap.get(ownerData);
//                String eClassName = ownerData.geteClassName();
//                String ePackageUri = ownerData.getePackageUri();
//                EPackage ePackage = resourceSet.getPackageRegistry().getEPackage(ePackageUri);
//                EClassifier eClass = ePackage.getEClassifier(eClassName);
//                assert eClass instanceof EClass;
//                int sfId = eObjectData.getFeatureId();
//                EStructuralFeature sf = ((EClass) eClass).getEStructuralFeature(sfId);
//                assert containerEObject != null;
//                // FIXME Ignores position for the moment
//                assignObjectToFeatureValue(containerEObject, sf, nodeEObject);
//            }
//            else if (childData instanceof ResourceContainerData) {
//                // All non contained EObjects will remain in the resource root.
//            }
//        }
//    }
//
//    private static Object getEDataTypeValue(ResourceSet resourceSet, EPackage ePackage, EDataTypeArtifactData dataTypeData) {
//        String dataEPackageUri = dataTypeData.getePackageUri();
//        EPackage dataEPackage;
//        if (dataEPackageUri != ePackage.getNsURI()) {
//            dataEPackage = resourceSet.getPackageRegistry().getEPackage(dataEPackageUri);
//            if (dataEPackage == null) {
//                throwPackageNotFound(dataEPackageUri);
//            }
//        }
//        else {
//            dataEPackage = ePackage;
//        }
//        EDataType eDataType = (EDataType) dataEPackage.getEClassifier(dataTypeData.getDataTypeName());
//        assert eDataType != null;
//        return dataEPackage.getEFactoryInstance().createFromString(eDataType, dataTypeData.getValue());
//    }
//
//    /**
//     * Check whether the required metamodels by the tree (resource) are loaded in the resource set. If not,
//     * try to load them using the persisted info
//     * @param resourceNode
//     * @param resourceSet
//     */
//    private static void checkMetamodels(Node.Op resourceNode, ResourceSet resourceSet) throws EccoException {
//        if (resourceNode.getArtifact().getData() instanceof EmfResourceData) {
//            HashMap<Object, Object> loadOptions = new HashMap<Object, Object>();
//            loadOptions.putAll(EmfPluginUtils.getDefaultLoadOptions());
//            EmfResourceData data = (EmfResourceData) resourceNode.getArtifact().getData();
//            if (data.getUsedPacakges().isEmpty()) {
//                throw new EccoException("There was no EPackage information found for the given resource node. The " +
//                        "Emf Resource can not he reconstructed without the EPackage information. Perhaps the Ecco " +
//                        "tree was not created with the Emf Reader and as a result the EmfResourceData usedPackages map " +
//                        "is empty.");
//            }
//            for (Map.Entry<String, EmfResourceData.EPackageLocation> entry : data.getUsedPacakges().entrySet()) {
//                String uri = entry.getKey();
//                Resource ePackageResource = resourceSet.getResource(URI.createURI(uri), false);
//                if (ePackageResource == null) {
//                    EmfResourceData.EPackageLocation loc = entry.getValue();
//                    if (loc.isLocal()) {
//                        URI fileUri = URI.createURI(loc.getLocationuri());
//                        ePackageResource = resourceSet.createResource(fileUri);
//                        try {
//                            ePackageResource.load(loadOptions);
//                            EObject eObject = ePackageResource.getContents().get(0);
//                            if (eObject instanceof EPackage) {
//                                EPackage ePackage = (EPackage)eObject;
//                                resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
//                                // FIXME for the writter, we need to now what factory and extension to use for each
//                                // package. The issue is that we can not persist the factory so probablt have to save
//                                // the class name and loaded from the class path?
////                                resourceSet.getResourceFactoryRegistry()
////                                        .getExtensionToFactoryMap()
////                                        .put("xmi", new XMIResourceFactoryImpl());
//                            }
//                        } catch (IOException e) {
//                            throw new EccoException(String.format("Package %s was found to be registered locally from %s, but there " +
//                                    "was an error when trying to reload the packge.", uri, loc.getLocationuri()), e);
//                        }
//                    }
//                    else {
//                        throwPackageNotFound(uri);
//                    }
//                }
//            }
//        }
//        else {
//            throw new EccoException("Emf Resources can only be reconstructed from Ecco trees that have a root node " +
//                    "that contains EmfResourceData. The given root node contains: " + resourceNode.getArtifact().getData());
//        }
//    }
//
//    private static void throwPackageNotFound(String uri) {
//        throw new EccoException(String.format("Package %s was found to be registered globally during " +
//                "loading, but it is not loaded in the active ResourceSet", uri));
//    }

}
