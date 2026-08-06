package at.jku.isse.ecco.plugin.artifact.uml;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryLabelProvider;
import org.eclipse.uml2.uml.*;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.edit.providers.UMLItemProviderAdapterFactory;
import org.eclipse.uml2.uml.internal.impl.ModelImpl;
import org.eclipse.uml2.uml.resource.UMLResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class UmlReader implements ArtifactReader<Path, Set<Node>> {

	private final EntityFactory entityFactory;

	@Inject
	public UmlReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return UmlPlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{"xmi", "uml"};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	@Override
	public boolean canRead(Path path) {
		// TODO: actually check contents of file to see if it is a text file
		if (!Files.isDirectory(path) && Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".xmi"))
			return true;
		else
			return false;
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {
		Set<Node> nodes = new HashSet<>();
		for (Path path : input) {
			Path resolvedPath = base.resolve(path);

			// PLUGIN NODE
			Artifact<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
			Node pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
			nodes.add(pluginNode);


			// EMF INIT
			ResourceSet resourceSet = new ResourceSetImpl();

			// for emf ecore namespace
			resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
			// for emf uml namespace
			resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);
//			// for gmf notation namespace
//			resourceSet.getPackageRegistry().put(NotationPackage.eNS_URI, NotationPackage.eINSTANCE);
//			// for papyrus style namespace
//			resourceSet.getPackageRegistry().put(StylePackage.eNS_URI, StylePackage.eINSTANCE);

			// register ".uml" file ending
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);
//			// register ".notation" file ending
//			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("notation", new GMFResourceFactory());

			Resource umlResource = resourceSet.getResource(URI.createFileURI(resolvedPath.toString()), true); // TODO: register all resources here at the same time so that references between them can be resolved!
//			Resource notationResource = resourceSet.getResource(URI.createFileURI(Paths.get("testdata/model.notation").toString()), true);

			EList<EObject> umlList = umlResource.getContents();
//			EList<EObject> notationList = notationResource.getContents();

			// for uml file
			ModelImpl umlModel = (ModelImpl) umlList.get(0);
			Model root = umlModel.getModel();

//			// for notation file
//			DiagramImpl notation = (DiagramImpl) notationList.get(0);
//			Diagram diagram = notation.getDiagram();


			Package umlPackage = UMLFactory.eINSTANCE.createPackage();

			UMLFactory.eINSTANCE.createAssociation();
			Property prop = UMLFactory.eINSTANCE.createProperty();
			prop.setAssociation(null);
			prop.setType(null);


			System.out.println("Processing Resources:");
			resourceSet.getResources().forEach(r -> {
				System.out.println(r);
			});

			// ################################################################################


			// node for model
			System.out.println(root.getName() + ", " + root);


			// comments
			for (Comment comment : root.getOwnedComments()) {
				for (Element element : comment.getAnnotatedElements()) {
					if (element instanceof Class) {
						System.out.println("comment owned by class....");
					}
				}
				System.out.println(comment);
			}


			// members
			for (NamedElement obj : root.getMembers()) {
				System.out.println("OBJECT: " + obj);

				// CLASS
				String typeString = obj.eClass().getName();
				System.out.println("CLASS: " + typeString);

				// ID
				UMLItemProviderAdapterFactory up = new UMLItemProviderAdapterFactory();
				AdapterFactoryLabelProvider p = new AdapterFactoryLabelProvider(up);
				String idString = p.getText(obj);
				System.out.println("ID: " + idString);


				if (typeString.equals("Association")) {
					Association association = (Association) obj;
					System.out.println(association.getName() + ", " + association.getQualifiedName() + ", " + association.toString());

					// node for association
					UmlAssociationArtifactData associationData = new UmlAssociationArtifactData(idString, association.getName(), typeString);
					Artifact associationArtifact = this.entityFactory.createArtifact(associationData);
					Node associationNode = this.entityFactory.createNode(associationArtifact);

					// process properties
					for (Property property : association.getMemberEnds()) {
						if (association.getOwnedEnds().contains(property)) {
							// set artifact reference with type "OwnedEnd"
//							associationArtifact.addUses(this.entityFactory.createArtifactReference(associationArtifact, this.getArtifactForObject(property.getType()), "OwnedEnd"));
						} else {
							// set artifact reference from association artifact to member end artifact with type "MemberEnd"
//							associationArtifact.addUses(this.entityFactory.createArtifactReference(associationArtifact, this.getArtifactForObject(property.getType()), "MemberEnd"));
						}
					}


				} else if (typeString.equals("Class")) {
					Class clazz = (Class) obj;
					System.out.println(clazz.getName() + ", " + clazz.getQualifiedName() + ", " + clazz.toString());

					// node for class
					UmlClassArtifactData classData = new UmlClassArtifactData(idString, clazz.getName(), typeString);
					Artifact classArtifact = this.entityFactory.createArtifact(classData);
					Node classNode = this.entityFactory.createNode(classArtifact);


					for (Property attribute : clazz.getOwnedAttributes()) {
						// add attributes as children
					}

					for (Operation operation : clazz.getOwnedOperations()) {
						// add operations as children

					}
				}


				// #########################################


//				// for every structural feature create node and artifact and add them as children
//				for (EStructuralFeature feature : obj.eClass().getEStructuralFeatures()) {
//					Node featureNode = this.createNodeForFeature(obj, feature);
//					elementNode.addChild(featureNode);
//				}
//
//				System.out.println("-----------------------------------");


				/**
				 * For now do this just so that it is enough for DPL.
				 *
				 * ASSOCIATION -> memberEnd properties -> Property -> TYPE and ASSOCIATION are references.
				 */


//				for (EStructuralFeature f : obj.eClass().getEStructuralFeatures()) {
//					System.out.println("F: " + f);
//					System.out.println("VAL: " + obj.eGet(f));
//				}
//
//				for (EAttribute f : obj.eClass().getEAllAttributes()) {
//					System.out.println("ALLA: " + f);
//					System.out.println("VAL: " + obj.eGet(f));
//				}


//				if (obj instanceof org.eclipse.uml2.uml.Class) {
//
//					System.out.println("NAME: " + ((Class) obj).getName() + ", " + ((Class) obj).getQualifiedName());
//
//					org.eclipse.uml2.uml.Class superClass = ((org.eclipse.uml2.uml.Class) obj).getSuperClass(null);
//					EList<Property> properties = ((org.eclipse.uml2.uml.Class) obj).getAllAttributes();
//					for (int j = 0; j < properties.size(); j++) {
//						Property prop = properties.get(j);
//						if (superClass != null) {
//							if (!superClass.getAllAttributes().contains(prop)) {
//
//							}
//						} else {
//							if (!prop.isComposite()) {
//
//							}
//						}
//
//					}
//
//					EList<org.eclipse.uml2.uml.Operation> operations = ((org.eclipse.uml2.uml.Class) obj).getOperations();
//					for (int j = 0; j < operations.size(); j++) {
//						org.eclipse.uml2.uml.Operation operation = operations.get(j);
//						if (superClass != null) {
//							if (!superClass.getOperations().contains(operation)) {
//
//							}
//						} else {
//
//						}
//
//					}
//
//					EList<Association> associatioList = ((org.eclipse.uml2.uml.Class) obj).getAssociations();
//					for (int j = 0; j < associatioList.size(); j++) {
//						Association association = associatioList.get(j);
//						EList<Property> associationClasses = association.getMemberEnds();
//
//						Property firstClass = associationClasses.get(0);
//						Property secondClass = associationClasses.get(1);
//
//
//					}
//
//
//				}

			}


		}
		return nodes;
	}


//	private Node createNodeForFeature(EObject object, EStructuralFeature feature) {
//		System.out.println("FEATURE: " + feature.getName() + ", " + feature);
//		Object value = object.eGet(feature);
//		System.out.println("VALUE: " + value);
//
//		if (feature.getName().equals("memberEnd")) {
//			System.out.println("CCC: " + value.getClass());
//		}
//
//		if (value instanceof List) {
//			UmlArtifactData data = new UmlArtifactData(feature.getName(), "LIST");
//			Artifact artifact = this.entityFactory.createArtifact(data);
//			Node node = this.entityFactory.createNode(artifact);
//
//			// recursively process value until it is not a list anymore. make list itself as a node (has a name, e.g. memberEnd) and put the list elements as its children.
//			List list = (List) value;
//			for (Object o : list) {
//				Node childNode = this.createNodeForObject(o);
//				node.addChild(childNode);
//			}
//
//			return null; // TODO
//		} else {
//			if (value instanceof Property) {
//				Property property = (Property) value;
//				System.out.println(property);
//				System.out.println(property.getName());
//				System.out.println(property.getQualifiedName());
//			}
//
//			// create node and artifact
//
//			return null; // TODO
//		}
//	}


	private Collection<ReadListener> listeners = new ArrayList<ReadListener>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
