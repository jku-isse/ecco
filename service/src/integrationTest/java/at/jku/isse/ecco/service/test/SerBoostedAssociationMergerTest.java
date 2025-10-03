package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.featuretrace.SerFeatureTrace;
import at.jku.isse.ecco.storage.ser.maintree.SerBoostedAssociationMerger;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class SerBoostedAssociationMergerTest {

    @Test
    public void boostProactiveTraceTest(){
        SerEntityFactory entityFactory = new SerEntityFactory();

        ArtifactData firstData = new LineArtifactData("Node with proactive feature trace");
        Artifact.Op<?> firstArtifact = entityFactory.createArtifact(firstData);
        Node.Op nodeWithProactiveTrace = entityFactory.createNode(firstArtifact);
        FeatureTrace traceWithProactiveCondition = new SerFeatureTrace(nodeWithProactiveTrace);
        traceWithProactiveCondition.setProactiveCondition("FeatureA");
        nodeWithProactiveTrace.setFeatureTrace(traceWithProactiveCondition);

        ArtifactData secondData = new LineArtifactData("Node without proactive feature trace");
        Artifact.Op<?> secondArtifact = entityFactory.createArtifact(secondData);
        Node.Op nodeWithoutProactiveTrace = entityFactory.createNode(secondArtifact);

        Set<Node.Op> nodes = new HashSet<>();
        nodes.add(nodeWithProactiveTrace);
        nodes.add(nodeWithoutProactiveTrace);

        Association.Op association = entityFactory.createAssociation(nodes);
        Collection<Association.Op> associations = new LinkedList<>();
        associations.add(association);

        SerBoostedAssociationMerger merger = new SerBoostedAssociationMerger();
        Node.Op mainTree = merger.buildMainTree(associations);

        List<? extends Node.Op> mainTreeNodes = mainTree.getChildren();
        assertEquals("FeatureA", mainTreeNodes.get(0).getFeatureTrace().getProactiveConditionString());
        assertEquals("FeatureA", mainTreeNodes.get(1).getFeatureTrace().getProactiveConditionString());
    }

    @Test
    public void dontBoostProactiveTraceTest(){
        SerEntityFactory entityFactory = new SerEntityFactory();

        ArtifactData firstData = new LineArtifactData("Node with proactive feature trace");
        Artifact.Op<?> firstArtifact = entityFactory.createArtifact(firstData);
        Node.Op nodeWithProactiveTrace = entityFactory.createNode(firstArtifact);
        FeatureTrace traceWithProactiveCondition = new SerFeatureTrace(nodeWithProactiveTrace);
        traceWithProactiveCondition.setProactiveCondition("FeatureA");
        nodeWithProactiveTrace.setFeatureTrace(traceWithProactiveCondition);

        ArtifactData secondData = new LineArtifactData("Node with conflicting proactive feature trace");
        Artifact.Op<?> secondArtifact = entityFactory.createArtifact(secondData);
        Node.Op nodeWithConflictingProactiveTrace = entityFactory.createNode(secondArtifact);
        FeatureTrace traceWithConflictingProactiveCondition = new SerFeatureTrace(nodeWithConflictingProactiveTrace);
        traceWithConflictingProactiveCondition.setProactiveCondition("FeatureB");
        nodeWithConflictingProactiveTrace.setFeatureTrace(traceWithConflictingProactiveCondition);

        Set<Node.Op> nodes = new HashSet<>();
        nodes.add(nodeWithProactiveTrace);
        nodes.add(nodeWithConflictingProactiveTrace);

        Association.Op association = entityFactory.createAssociation(nodes);
        Collection<Association.Op> associations = new LinkedList<>();
        associations.add(association);

        SerBoostedAssociationMerger merger = new SerBoostedAssociationMerger();
        Node.Op mainTree = merger.buildMainTree(associations);

        List<? extends Node.Op> mainTreeNodes = mainTree.getChildren();
        assertEquals("FeatureA", mainTreeNodes.get(0).getFeatureTrace().getProactiveConditionString());
        assertEquals("FeatureB", mainTreeNodes.get(1).getFeatureTrace().getProactiveConditionString());
    }
}
