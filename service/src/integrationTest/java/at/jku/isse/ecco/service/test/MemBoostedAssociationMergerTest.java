package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.storage.mem.maintree.MemBoostedAssociationMerger;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.mem.featuretrace.MemFeatureTrace;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class MemBoostedAssociationMergerTest {

    @Test
    public void boostProactiveTraceTest(){
        MemEntityFactory entityFactory = new MemEntityFactory();

        ArtifactData firstData = new LineArtifactData("Node with proactive feature trace");
        Artifact.Op<?> firstArtifact = entityFactory.createArtifact(firstData);
        Node.Op nodeWithProactiveTrace = entityFactory.createNode(firstArtifact);
        FeatureTrace traceWithProactiveCondition = new MemFeatureTrace(nodeWithProactiveTrace);
        traceWithProactiveCondition.setUserCondition("FeatureA");
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

        MemBoostedAssociationMerger merger = new MemBoostedAssociationMerger();
        Node.Op mainTree = merger.buildMainTree(associations);

        List<? extends Node.Op> mainTreeNodes = mainTree.getChildren();
        assertEquals("FeatureA", mainTreeNodes.get(0).getFeatureTrace().getUserConditionString());
        assertEquals("FeatureA", mainTreeNodes.get(1).getFeatureTrace().getUserConditionString());
    }

    @Test
    public void dontBoostProactiveTraceTest(){
        MemEntityFactory entityFactory = new MemEntityFactory();

        ArtifactData firstData = new LineArtifactData("Node with proactive feature trace");
        Artifact.Op<?> firstArtifact = entityFactory.createArtifact(firstData);
        Node.Op nodeWithProactiveTrace = entityFactory.createNode(firstArtifact);
        FeatureTrace traceWithProactiveCondition = new MemFeatureTrace(nodeWithProactiveTrace);
        traceWithProactiveCondition.setUserCondition("FeatureA");
        nodeWithProactiveTrace.setFeatureTrace(traceWithProactiveCondition);

        ArtifactData secondData = new LineArtifactData("Node with conflicting proactive feature trace");
        Artifact.Op<?> secondArtifact = entityFactory.createArtifact(secondData);
        Node.Op nodeWithConflictingProactiveTrace = entityFactory.createNode(secondArtifact);
        FeatureTrace traceWithConflictingProactiveCondition = new MemFeatureTrace(nodeWithConflictingProactiveTrace);
        traceWithConflictingProactiveCondition.setUserCondition("FeatureB");
        nodeWithConflictingProactiveTrace.setFeatureTrace(traceWithConflictingProactiveCondition);

        Set<Node.Op> nodes = new HashSet<>();
        nodes.add(nodeWithProactiveTrace);
        nodes.add(nodeWithConflictingProactiveTrace);

        Association.Op association = entityFactory.createAssociation(nodes);
        Collection<Association.Op> associations = new LinkedList<>();
        associations.add(association);

        MemBoostedAssociationMerger merger = new MemBoostedAssociationMerger();
        Node.Op mainTree = merger.buildMainTree(associations);

        List<? extends Node.Op> mainTreeNodes = mainTree.getChildren();
        assertEquals("FeatureA", mainTreeNodes.get(0).getFeatureTrace().getUserConditionString());
        assertEquals("FeatureB", mainTreeNodes.get(1).getFeatureTrace().getUserConditionString());
    }
}
