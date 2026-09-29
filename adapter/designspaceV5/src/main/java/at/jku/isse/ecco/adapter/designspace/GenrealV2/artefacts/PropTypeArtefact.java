package at.jku.isse.ecco.adapter.designspace.GenrealV2.artefacts;

import at.jku.isse.designspace.commons.Cardinality;
import at.jku.isse.designspace.commons.Key;
import at.jku.isse.designspace.commons.OrderedSet;
import at.jku.isse.designspace.core.model.DesignSpace;
import at.jku.isse.designspace.core.model.WorkspaceElement;
import at.jku.isse.designspace.core.model.WorkspacePropertyType;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.DesignspaceWriter;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.TreeLogger;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.refFixUp.CollectionFixUp;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.refFixUp.MapFixUp;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.refFixUp.SingleFixUp;
import at.jku.isse.ecco.adapter.designspace.artifact.StringArtefact;
import at.jku.isse.ecco.adapter.designspace.artifact.value.ReferenceValueArtefact;
import at.jku.isse.ecco.adapter.designspace.artifact.value.SimpleValueArtifact;
import at.jku.isse.ecco.adapter.designspace.artifact.value.ValueArtefact;
import at.jku.isse.ecco.adapter.designspace.exception.NodeWrongArtefact;
import at.jku.isse.ecco.adapter.designspace.exception.TypeMangerException;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.tree.Node;
import jdk.jshell.spi.ExecutionControl;

import java.util.*;

public class PropTypeArtefact implements ArtifactData {
    public final String qualifiedName;
    public final Cardinality cardinality;

    public PropTypeArtefact(String qualifiedName, Cardinality cardinality) {
        this.qualifiedName = qualifiedName;
        this.cardinality = cardinality;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PropTypeArtefact that = (PropTypeArtefact) o;
        return Objects.equals(qualifiedName, that.qualifiedName) && cardinality == that.cardinality;
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualifiedName, cardinality);
    }


    public void build(Node propertyTypeNode, at.jku.isse.designspace.core.model.WorkspaceElement owningElement, DesignspaceWriter designspaceWriter) {


        try (var scope = TreeLogger.enter("Property: " + qualifiedName + " (" + cardinality + ")")) {

            if (cardinality.equals(Cardinality.SINGLE)) {
                if (propertyTypeNode.getChildren().isEmpty()) {
                    TreeLogger.log("(empty single property)");
                    return;
                }
                handleSingleValue(owningElement, propertyTypeNode.getChildren().get(0), designspaceWriter);
            }  else if (cardinality.equals(Cardinality.MAP)) {

                WorkspacePropertyType propertyType = DesignSpace.getPropertyType(qualifiedName);
                Map<Key, Node> map = new HashMap<>();
                for (Node keyNode : propertyTypeNode.getChildren()) {
                    StringArtefact keyArtefact = (StringArtefact) keyNode.getArtifact().getData();
                    map.put(Key.of(keyArtefact.getValue()), keyNode.getChildren().get(0));

                }
                setMapPropValue(owningElement, propertyType, map,designspaceWriter);
            }

            else {
                WorkspacePropertyType propertyType = DesignSpace.getPropertyType(qualifiedName);
                setCollectionPropValue(owningElement, propertyType, propertyTypeNode.getChildren(), designspaceWriter);
            }
        }
    }

    public void handleSingleValue(at.jku.isse.designspace.core.model.WorkspaceElement owningElement, Node value, DesignspaceWriter designspaceWriter) {

        WorkspacePropertyType propertyType = DesignSpace.getPropertyType(qualifiedName);

        //if (propertyType.isContained()) { // have to investiaet this for it handeling opposed props
        //    return;
        //}

        if (value.getArtifact().getData() instanceof ReferenceArtefact refArtefact) {
            designspaceWriter.fixups.add(new SingleFixUp(owningElement,propertyType,refArtefact));
        }
        else if (value.getArtifact().getData() instanceof WorkspaceElementArtefact workspaceElementArtefact) {
            at.jku.isse.designspace.core.model.WorkspaceElement child = null;
            try {
                child = workspaceElementArtefact.build(value, designspaceWriter);
            } catch (NodeWrongArtefact | TypeMangerException | ExecutionControl.NotImplementedException e) {
                throw new RuntimeException(e);
            }
            owningElement.set(propertyType, child);


        } else if (value.getArtifact().getData() instanceof SimpleValueArtifact<?> valueArtifact) {
            owningElement.set(propertyType, valueArtifact.getValue());
        } else throw new RuntimeException("unexpected value");
    }


    private void setMapPropValue(WorkspaceElement instance, WorkspacePropertyType propertyType, Map<Key, Node> artefactMap, DesignspaceWriter designspaceWriter)  {
        if (artefactMap.isEmpty()) return;
        //if (propertyType.isContained()) {
        //    return;
        //}
        Node example = artefactMap.values().stream().findAny().orElse(null);

        if (example.getArtifact().getData() instanceof ReferenceArtefact refArtefact) {
            Map<Key, ReferenceArtefact> refMap = new HashMap<>();

            for (Map.Entry<Key, Node> entry : artefactMap.entrySet()) {
                refMap.put(entry.getKey(), (ReferenceArtefact) entry.getValue().getArtifact().getData());
            }
            designspaceWriter.fixups.add(new MapFixUp(instance,propertyType,refMap));
        } else if (example.getArtifact().getData() instanceof SimpleValueArtifact<?>) {
            Map<Key, Object> map = new HashMap<>();
            artefactMap.forEach((key, value) -> {
                if (value.getArtifact().getData() instanceof SimpleValueArtifact<?> simpleValue) {
                    map.put(key, simpleValue.getValue());
                }
            });
            instance.setAll(propertyType, map);
        } else throw new RuntimeException("unexpected value");
    }

    private void setCollectionPropValue(at.jku.isse.designspace.core.model.WorkspaceElement instance, WorkspacePropertyType propertyType, List<? extends Node> valueNodeCollection, DesignspaceWriter designspaceWriter) {

        if (valueNodeCollection.isEmpty()) {
            return;
        }

        //if (propertyType.isContained()) {
        //    return;
        //}


        Node example = valueNodeCollection.stream().findAny().orElse(null); // they should all be the same artefact
        if (example == null) {
            System.err.println("null collection in TypeArtefact");
            return;
        }
        if (example.getArtifact().getData() instanceof ReferenceArtefact) {
            Collection<ReferenceArtefact> collection;
            if (propertyType.getCardinality().equals(Cardinality.UNORDERED_SET) || propertyType.getCardinality().equals(Cardinality.ORDERED_SET))
                collection = new OrderedSet<>();
            else if (propertyType.getCardinality().equals(Cardinality.LIST)) collection = new ArrayList<>();
            else throw new RuntimeException("ListSetArtefact received invalid Cardinality");


            valueNodeCollection.forEach((value) -> {
                if (value.getArtifact().getData() instanceof ReferenceArtefact refArtefact) {
                    collection.add(refArtefact);
                }
            });

            designspaceWriter.fixups.add(new CollectionFixUp(instance,propertyType,collection));
        }
        else if (example.getArtifact().getData() instanceof WorkspaceElementArtefact) {
            Collection<at.jku.isse.designspace.core.model.WorkspaceElement> collection;
            if (propertyType.getCardinality().equals(Cardinality.UNORDERED_SET) || propertyType.getCardinality().equals(Cardinality.ORDERED_SET))
                collection = new OrderedSet<>();
            else if (propertyType.getCardinality().equals(Cardinality.LIST)) collection = new ArrayList<>();
            else throw new RuntimeException("ListSetArtefact received invalid Cardinality");


            valueNodeCollection.forEach((value) -> {
                if (value.getArtifact().getData() instanceof WorkspaceElementArtefact workspaceElementArtefact) {
                    try {
                        collection.add(workspaceElementArtefact.build(value, designspaceWriter));
                    } catch (NodeWrongArtefact | ExecutionControl.NotImplementedException | TypeMangerException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            //System.out.println("CollectionProps " + qualifiedName +" for " + instance+" order is for retrieved elements " +Arrays.toString(collection.toArray()) );
            instance.setAll(propertyType, collection);


        } else if (example.getArtifact().getData() instanceof SimpleValueArtifact<?>) {
            Collection<Object> collection;
            if (propertyType.getCardinality().equals(Cardinality.UNORDERED_SET) || propertyType.getCardinality().equals(Cardinality.ORDERED_SET))
                collection = new OrderedSet<>();
            else if (propertyType.getCardinality().equals(Cardinality.LIST)) collection = new ArrayList<>();
            else throw new RuntimeException("ListSetArtefact received invalid Cardinality");
            valueNodeCollection.forEach((value) -> {
                if (value.getArtifact().getData() instanceof SimpleValueArtifact<?> simpleValueArtifact) {
                    collection.add(simpleValueArtifact.getValue());
                }
            });
            instance.setAll(propertyType, collection);
            //System.out.println("CollectionProps " + qualifiedName +" for " + instance+" order is for retrieved elements " +Arrays.toString(collection.toArray()) );
        } else throw new RuntimeException("unexpected value");
    }

}
