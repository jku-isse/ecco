package at.jku.isse.ecco.adapter.ds.util;

import at.jku.isse.designspace.core.model.Cardinality;
import at.jku.isse.designspace.variant.dto.*;
import at.jku.isse.ecco.adapter.ds.artifacts.*;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

public class DsDtoFactory {

    public static FolderDto createFolderDto(Node folderNode){
        assert(folderNode.getArtifact().getData() instanceof FolderArtifactData);
        FolderArtifactData folderArtifactData = (FolderArtifactData) folderNode.getArtifact().getData();

        Set<InstanceTypeDto> instanceTypeDtos = new HashSet<>();
        for (Node instanceTypeNode : folderNode.getChildren()){
            instanceTypeDtos.add(createIntanceTypeDto(instanceTypeNode));
        }
        return new FolderDto(folderArtifactData.getName(), instanceTypeDtos);
    }

    public static InstanceTypeDto createIntanceTypeDto(Node instanceTypeNode){
        assert(instanceTypeNode.getArtifact().getData() instanceof InstanceTypeArtifactData);
        InstanceTypeArtifactData instanceTypeArtifactData = (InstanceTypeArtifactData) instanceTypeNode.getArtifact().getData();

        List<InstanceDto> instanceDtos = new LinkedList<>();
        for (Node instanceNode : instanceTypeNode.getChildren()){
            instanceDtos.add(createInstanceDto(instanceNode));
        }
        return new InstanceTypeDto(instanceTypeArtifactData.getId(), instanceTypeArtifactData.getName(), instanceDtos);
    }

    public static InstanceDto createInstanceDto(Node instanceNode){
        assert(instanceNode.getArtifact().getData() instanceof InstanceArtifactData);
        InstanceArtifactData instanceArtifactData = (InstanceArtifactData) instanceNode.getArtifact().getData();

        Set<PropertyDto> propertyDtos = new HashSet<>();
        for (Node propertyNode : instanceNode.getChildren()){
            propertyDtos.add(createPropertyDto(propertyNode));
        }
        return new InstanceDto(instanceArtifactData.getName(), instanceArtifactData.getId(), propertyDtos);
    }

    public static PropertyDto createPropertyDto(Node propertyNode){
        assert(propertyNode.getArtifact().getData() instanceof PropertyArtifactData);
        PropertyArtifactData propertyArtifactData = (PropertyArtifactData) propertyNode.getArtifact().getData();

        Cardinality cardinality = propertyArtifactData.getCardinality();
        return switch (cardinality) {
            case SINGLE -> createSinglePropertyDto(propertyNode, propertyArtifactData);
            case LIST -> createListPropertyDto(propertyNode, propertyArtifactData);
            case SET -> createSetPropertyDto(propertyNode, propertyArtifactData);
            case MAP -> createMapPropertyDto(propertyNode, propertyArtifactData);
        };
    }
    
    public static SinglePropertyDto createSinglePropertyDto(Node singlePropertyNode, PropertyArtifactData propertyArtifactData){
        List<? extends Node> children = singlePropertyNode.getChildren();
        ValueDto valueDto;
        if (children.size() > 0){
            valueDto = createValueDto(singlePropertyNode.getChildren().get(0).getArtifact().getData());
        } else {
            valueDto = null;
        }
        return new SinglePropertyDto(propertyArtifactData.getName(), valueDto);
    }

    public static ListPropertyDto createListPropertyDto(Node listPropertyNode, PropertyArtifactData propertyArtifactData){
        List<ValueDto> valueDtoList = new LinkedList<>();
        for (Node valueNode : listPropertyNode.getChildren()){
            valueDtoList.add(createValueDto(valueNode.getArtifact().getData()));
        }
        return new ListPropertyDto(propertyArtifactData.getName(), valueDtoList);
    }

    public static SetPropertyDto createSetPropertyDto(Node setPropertyNode, PropertyArtifactData propertyArtifactData){
        Set<ValueDto> valueDtoSet = new HashSet<>();
        for (Node valueNode : setPropertyNode.getChildren()){
            valueDtoSet.add(createValueDto(valueNode.getArtifact().getData()));
        }
        return new SetPropertyDto(propertyArtifactData.getName(), valueDtoSet);
    }

    public static MapPropertyDto createMapPropertyDto(Node propertyNode, PropertyArtifactData propertyArtifactData){
        Map<String, ValueDto> valueDtoMap = new HashMap<>();

        for (Node valueNode : propertyNode.getChildren()){
            MapValueArtifactData mapValueArtifactData = (MapValueArtifactData) valueNode.getArtifact().getData();
            ValueDto valueDto = createValueDto(mapValueArtifactData.getValueArtifactData());
            valueDtoMap.put(mapValueArtifactData.getKey(), valueDto);
        }
        return new MapPropertyDto(propertyArtifactData.getName(), valueDtoMap);
    }
    
    public static ValueDto createValueDto(ArtifactData artifactData){
        assert(artifactData instanceof ValueArtifactData);

        //if (artifactData instanceof SimpleValueArtifactData<?>){
        if (artifactData instanceof SimpleValueArtifactData){
            return createSimpleValueDto((SimpleValueArtifactData) artifactData);

        } else if (artifactData instanceof ReferenceValueArtifactData){
            return createReferenceValueDto((ReferenceValueArtifactData) artifactData);

        } else if (artifactData instanceof MapValueArtifactData){
            throw new RuntimeException("MapValueArtifactData in child-node of non-MapProperty-node");

        } else {
            throw new RuntimeException("Unknown subtype of ValueArtifactData");
        }
    }

    public static <T> SimpleValueDto<T> createSimpleValueDto(SimpleValueArtifactData<T> simpleValueArtifactData){
        return new SimpleValueDto<>(simpleValueArtifactData.getValue());
    }

    public static ReferenceValueDto createReferenceValueDto(ReferenceValueArtifactData referenceValueArtifactData){
        return new ReferenceValueDto(referenceValueArtifactData.getReferencedId());
    }
}
