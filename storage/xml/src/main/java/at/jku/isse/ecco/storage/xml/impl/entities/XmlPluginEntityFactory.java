package at.jku.isse.ecco.storage.xml.impl.entities;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

import static java.util.Objects.requireNonNull;


public class XmlPluginEntityFactory implements EntityFactory {
    @Override
    public Remote createRemote(String name, String address, Remote.Type type) {
        return new XmlRemote(name, address, type);
    }

    @Override
    public Commit createCommit() {
        return new XmlCommit();
    }

    @Override
    public Configuration createConfiguration(FeatureRevision[] featureRevisions) {
        return new XmlConfiguration(featureRevisions);
    }

    @Override
    public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
        return new XmlArtifact<>(data);
    }

    @Override
    public XmlAssociation createAssociation() {
        return new XmlAssociation();
    }

    @Override
    public Association.Op createAssociation(Set<Node.Op> nodes) {
        requireNonNull(nodes);
        assert !nodes.isEmpty();
        final XmlAssociation association = createAssociation();
        RootNode.Op rootNode = createRootNode();
        rootNode.setContainingAssociation(association);
        for (Node.Op node : nodes) {
            rootNode.addChild(node);
        }
        association.setRootNode(rootNode);
        return association;
    }

    @Override
    public Feature createFeature(String id, String name) {
        requireNonNull(name);
        assert !name.isEmpty() : "Expected a non-empty name but was empty.";
        return new XmlFeature(id, name);
    }

    @Override
    public RootNode.Op createRootNode() {
        return new XmlNode.XmlRootNode();
    }

    @Override
    public Node.Op createNode() {
        return new XmlNode();
    }

    @Override
    public Node.Op createNode(Artifact.Op<?> artifact) {
        requireNonNull(artifact);
        final Node.Op node = createNode();
        node.setArtifact(artifact);
        artifact.setContainingNode(node);
        return node;
    }

    @Override
    public Node.Op createNode(ArtifactData artifactData) {
        return createNode(createArtifact(artifactData));
    }

    @Override
    public Node.Op createOrderedNode(Artifact.Op<?> artifact) {
        requireNonNull(artifact);
        final Node.Op node = createNode(artifact);
        artifact.setOrdered(true);
        return node;
    }

    @Override
    public Node.Op createOrderedNode(ArtifactData artifactData) {
        return createOrderedNode(createArtifact(artifactData));
    }


}
