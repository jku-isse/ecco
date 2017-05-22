package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.artifact.emf.util.EmfPluginUtils;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Given a Ecco tree, this class can reconstruct the EMF resource represented by the tree.
 *
 * TODO Do we need to handle partial models?
 * Created by hhoyos on 22/05/2017.
 */
public class EmfReconstruct {

    public static Resource reconstructResource(Node.Op node, ResourceSet resourceSet) {
        Resource resource = null;
        if (node.getArtifact().getData() instanceof PluginArtifactData) {
            // The plugin single child should be the resource node
            Node.Op resourceNode = node.getChildren().get(0);
            checkMetamodels(resourceNode, resourceSet);
            // Create the resource, the uri can be assigned later, e.g. the writer will assing an uri to persist.
            String ext = ((EmfResourceData) resourceNode.getArtifact().getData()).getExtension();
            resource = resourceSet.createResource(URI.createURI("." + ext));
            for (Node.Op child : resourceNode.getChildren()) {
                EObject eObject = createEObjectStructure(child, resourceSet);
                resource.getContents().add(eObject);
            }
        }
        else {
            System.out.println("Handle other type of nodes: " + node.getArtifact().getData());
        }
        return resource;
    }

    private static EObject createEObjectStructure(Node.Op node, ResourceSet resourceSet) throws EccoException {
        EObject eObject = null;
        if (node.getArtifact().getData() instanceof EObjectArtifactData) {
            EObjectArtifactData data = (EObjectArtifactData) node.getArtifact().getData();
            String eClassName = data.geteClassName();
            String ePackageUri = data.getePackageUri();
            EPackage ePackage = resourceSet.getPackageRegistry().getEPackage(ePackageUri);
            EClassifier eClass = ePackage.getEClassifier(eClassName);
            assert eClass instanceof EClass;
            eObject = ePackage.getEFactoryInstance().create((EClass) eClass);
            // Add all features
            for (Node.Op child : node.getChildren()) {
                if (child.getArtifact().getData() instanceof EDataTypeArtifactData) {
                    EDataTypeArtifactData dataTypeData = (EDataTypeArtifactData) child.getArtifact().getData();
                    int sfId = dataTypeData.getFeatureId();
                    EStructuralFeature sf = ((EClass) eClass).getEStructuralFeature(sfId);
                    Object value = getEDataTypeValue(resourceSet, ePackage, dataTypeData);
                    if (sf.isMany()) {
                        // FIXME Ignores position for the moment
                        EList<Object> values = (EList<Object>) eObject.eGet(sf);
                        values.add(value);
                    } else {
                        eObject.eSet(sf, value);
                    }
                } else if (child.getArtifact().getData() instanceof EObjectArtifactData) {
                    // Containment is trickier, as we need to account for positions, if needed
                    EObjectArtifactData eObjectData = (EObjectArtifactData) child.getArtifact().getData();
                    int sfId = eObjectData.getFeatureId();
                    EStructuralFeature sf = ((EClass) eClass).getEStructuralFeature(sfId);
                    EObject childEObject = createEObjectStructure(child, resourceSet);
                    assert childEObject != null;
                    if (sf.isMany()) {
                        // FIXME Ignores position for the moment
                        EList<EObject> values = (EList<EObject>) eObject.eGet(sf);
                        values.add(childEObject);
                    } else {
                        eObject.eSet(sf, childEObject);
                    }
                }
                // FIXME do we need to do at the end like when reading?
                else if (child.getArtifact().getData() instanceof EReference) {
                    System.out.println("ERefernces not implemented.");
                }
            }

        }
        return eObject;
    }

    private static Object getEDataTypeValue(ResourceSet resourceSet, EPackage ePackage, EDataTypeArtifactData dataTypeData) {
        String dataEPackageUri = dataTypeData.getePackageUri();
        EPackage dataEPackage;
        if (dataEPackageUri != ePackage.getNsURI()) {
            dataEPackage = resourceSet.getPackageRegistry().getEPackage(dataEPackageUri);
            if (dataEPackage == null) {
                throwPackageNotFound(dataEPackageUri);
            }
        }
        else {
            dataEPackage = ePackage;
        }
        EDataType eDataType = (EDataType) dataEPackage.getEClassifier(dataTypeData.getDataTypeName());
        assert eDataType != null;
        return dataEPackage.getEFactoryInstance().createFromString(eDataType, dataTypeData.getValue());
    }

    /**
     * Check whether the required metamodels by the tree (resource) are loaded in the resource set. If not,
     * try to load them using the persisted info
     * @param resourceNode
     * @param resourceSet
     */
    private static void checkMetamodels(Node.Op resourceNode, ResourceSet resourceSet) throws EccoException {
        if (resourceNode.getArtifact().getData() instanceof EmfResourceData) {
            HashMap<Object, Object> loadOptions = new HashMap<Object, Object>();
            loadOptions.putAll(EmfPluginUtils.getDefaultLoadOptions());
            EmfResourceData data = (EmfResourceData) resourceNode.getArtifact().getData();
            for (Map.Entry<String, EmfResourceData.EPackageLocation> entry : data.getUsedPacakges().entrySet()) {
                String uri = entry.getKey();
                Resource ePackageResource = resourceSet.getResource(URI.createURI(uri), false);
                if (ePackageResource == null) {
                    EmfResourceData.EPackageLocation loc = entry.getValue();
                    if (loc.isLocal()) {
                        URI fileUri = URI.createURI(loc.getLocationuri());
                        ePackageResource = resourceSet.createResource(fileUri);
                        try {
                            ePackageResource.load(loadOptions);
                            EObject eObject = ePackageResource.getContents().get(0);
                            if (eObject instanceof EPackage) {
                                EPackage ePackage = (EPackage)eObject;
                                resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
                                // FIXME for the writter, we need to now what factory and extension to use for each
                                // package. The issue is that we can not persist the factory so probablt have to save
                                // the class name and loaded from the class path?
//                                resourceSet.getResourceFactoryRegistry()
//                                        .getExtensionToFactoryMap()
//                                        .put("xmi", new XMIResourceFactoryImpl());
                            }
                        } catch (IOException e) {
                            throw new EccoException(String.format("Package %s was found to be rigistered locally from %s, but there " +
                                    "was an error when trying to reload the packge.", uri, loc.getLocationuri()), e);
                        }
                    }
                    else {
                        throwPackageNotFound(uri);
                    }
                }
            }

        }
        else {
            throw new EccoException("Emf Resources can only be reconstructed from Ecco trees that have a root node " +
                    "that contains EmfResourceData. The given root node contains: " + resourceNode.getArtifact().getData());
        }
    }

    private static void throwPackageNotFound(String uri) {
        throw new EccoException(String.format("Package %s was found to be registered globally during " +
                "loading, but it is not loaded in the active ResourceSet", uri));
    }

}
