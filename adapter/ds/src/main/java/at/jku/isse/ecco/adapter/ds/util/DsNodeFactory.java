package at.jku.isse.ecco.adapter.ds.util;

import at.jku.isse.designspace.variant.dto.*;
import at.jku.isse.ecco.adapter.ds.artifacts.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.util.Map;

public class DsNodeFactory {

    private final EntityFactory entityFactory;

    public DsNodeFactory(EntityFactory entityFactory){
        this.entityFactory = entityFactory;
    }

    public Node.Op createFolderNode(FolderDto folderDto){
        FolderArtifactData folderArtifactData = new FolderArtifactData(folderDto);
        Artifact.Op<FolderArtifactData> folderArtifact = this.entityFactory.createArtifact(folderArtifactData);
        Node.Op folderNode = this.entityFactory.createNode(folderArtifact);

        for (InstanceTypeDto instanceTypeDto : folderDto.getInstanceTypeDtos()) {
            Node.Op instanceTypeNode = this.createInstanceTypeNode(instanceTypeDto);
            folderNode.addChild(instanceTypeNode);
        }
        return folderNode;
    }

    public Node.Op createInstanceTypeNode(InstanceTypeDto instanceTypeDto){
        InstanceTypeArtifactData instanceTypeArtifactData = new InstanceTypeArtifactData(instanceTypeDto);
        Artifact.Op<InstanceTypeArtifactData> instanceTypeArtifact = this.entityFactory.createArtifact(instanceTypeArtifactData);
        Node.Op instanceTypeNode = this.entityFactory.createNode(instanceTypeArtifact);

        for (InstanceDto instanceDto : instanceTypeDto.getInstanceDtos()) {
            Node.Op instanceNode = this.createInstanceNode(instanceDto);
            instanceTypeNode.addChild(instanceNode);
        }
        return instanceTypeNode;
    }

    public Node.Op createInstanceNode(InstanceDto instanceDto){
        InstanceArtifactData instanceArtifactData = new InstanceArtifactData(instanceDto);
        Artifact.Op<InstanceArtifactData> instanceArtifact = this.entityFactory.createArtifact(instanceArtifactData);
        Node.Op instanceNode = this.entityFactory.createNode(instanceArtifact);

        for (PropertyDto propertyDto : instanceDto.getPropertyDtos()) {
            Node.Op propertyNode = this.createPropertyNode(propertyDto);
            instanceNode.addChild(propertyNode);
        }
        return instanceNode;
    }

    public Node.Op createPropertyNode(PropertyDto propertyDto){
        PropertyArtifactData propertyArtifactData = new PropertyArtifactData(propertyDto);
        Artifact.Op<PropertyArtifactData> propertyArtifact = this.entityFactory.createArtifact(propertyArtifactData);
        Node.Op propertyNode = this.entityFactory.createNode(propertyArtifact);

        if (propertyDto instanceof SinglePropertyDto singlePropertyDto){
            ValueDto valueDto = singlePropertyDto.getValueDto();
            if (valueDto != null) {
                Node.Op valueNode = this.createValueNode(valueDto);
                propertyNode.addChild(valueNode);
            }
        } else if (propertyDto instanceof ListPropertyDto listPropertyDto){
            for (ValueDto<?> valueDto : listPropertyDto.getValueDtoList()) {
                Node.Op valueNode = this.createValueNode(valueDto);
                propertyNode.addChild(valueNode);
            }
        } else if(propertyDto instanceof SetPropertyDto setPropertyDto) {
            for (ValueDto<?> valueDto : setPropertyDto.getValueDtoSet()) {
                Node.Op valueNode = this.createValueNode(valueDto);
                propertyNode.addChild(valueNode);
            }
        } else if (propertyDto instanceof MapPropertyDto mapPropertyDto){
            for (Map.Entry<String, ValueDto> entry : mapPropertyDto.getValueDtoMap().entrySet()) {
                Node.Op mapValueNode = this.createMapValueNode(entry);
                propertyNode.addChild(mapValueNode);
            }
        } else {
            throw new RuntimeException("Unknown property type: " + propertyDto.getClass().getSimpleName());
        }

        return propertyNode;
    }

    public Node.Op createMapValueNode(Map.Entry<String, ValueDto> entry){
        ValueArtifactData valueArtifactData = this.createValueArtifactData(entry.getValue());
        MapValueArtifactData mapValueArtifactData = new MapValueArtifactData(entry.getKey(), valueArtifactData);
        Artifact.Op<MapValueArtifactData> mapValueArtifact = this.entityFactory.createArtifact(mapValueArtifactData);
        return this.entityFactory.createNode(mapValueArtifact);
    }

    public Node.Op createValueNode(ValueDto<?> valueDto){
        ValueArtifactData valueArtifactData = this.createValueArtifactData(valueDto);
        Artifact.Op<ValueArtifactData> valueArtifact = this.entityFactory.createArtifact(valueArtifactData);
        return this.entityFactory.createNode(valueArtifact);
    }

    private ValueArtifactData createValueArtifactData(ValueDto<?> valueDto){
        if (valueDto instanceof ReferenceValueDto referenceValueDto){
            return new ReferenceValueArtifactData(referenceValueDto);

        } else if (valueDto instanceof SimpleValueDto<?> simpleValueDto){
            return new SimpleValueArtifactData(simpleValueDto);

        } else {
            throw new RuntimeException("Unknown value type: " + valueDto.getClass().getSimpleName());
        }
    }
}
