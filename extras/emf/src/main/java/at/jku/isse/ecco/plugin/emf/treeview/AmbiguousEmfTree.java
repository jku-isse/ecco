package at.jku.isse.ecco.plugin.emf.treeview;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.plugin.emf.data.*;
import at.jku.isse.ecco.tree.Node;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Given an ECCO tree (composed from artifacts) this class constructs an Ambiguous EMF Model
 * that can be shown in the ECO GUI to inform the user about ambiguities in the composed
 * model.
 *
 * Red show fatal ambiguities, e.g. a single-valued feature with two different values, element
 * contained in two places, ordered multi-valued feature with diff order
 * Orange severe, e.g. unordered multi-valued feature with diff elements
 *
 */
public class AmbiguousEmfTree {

    private final List<EmfTreeNode> children = new ArrayList<>();
    private static boolean isAmbiguous = false;

    public AmbiguousEmfTree(Node.Op resourceNode) {
        assert resourceNode.getArtifact().getData() instanceof EmfResourceData;
        for (Node.Op child : resourceNode.getChildren()) {
                EmfTreeNode node = createEObjectNode(child);
                // Only add if it does not have an EContainerData Node
                if (!child.getChildren().stream()
                        .anyMatch(n -> n.getArtifact().getData() instanceof EContainerData)) {
                    getChildren().add(node);
                }
        }
        for (Node.Op child : resourceNode.getChildren()) {
            createEObjectNodeChildren(child);
        }
    }

    public List<EmfTreeNode> getChildren() {
        return children;
    }

    public boolean isAmbiguous() {
        return isAmbiguous;
    }

    private EmfTreeNode createEObjectNode(Node.Op eccoNode) {
        EObjectArtifactData data = (EObjectArtifactData) eccoNode.getArtifact().getData();
        EmfTreeNode.EObjectNode node = new EmfTreeNode.EObjectNode(null, data);
        return node;
    }

    private static void createEObjectNodeChildren(Node.Op eccoNode) {
        // Group children by feature id to detect ambiguities
        EmfTreeNode node = EmfTreeNode.EObjectNode.trace.get(eccoNode.getArtifact().getData());
        // First add the eContainer, if present
        List<EContainerData> eContainerData = eccoNode.getChildren().stream()
                .filter(ch -> ch.getArtifact().getData() instanceof EContainerData)
                .map(ecd -> ecd.getArtifact().getData())
                .map(e -> (EContainerData)e)
                .collect(Collectors.toList());
        if (eContainerData.size() > 1) {      // Ambiguous
            BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode> factory = dispatchAmbiguous.get(EContainerData.class);
            factory.apply(eContainerData, node);
        }
        else if (eContainerData.size() == 1) {
            BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> factory = dispatch.get(EContainerData.class);
            factory.apply(eContainerData.get(0), node);
        }
        // Instead of a map use a list, given that we don't really need the keys.
        Map<Integer, List<StructuralFeatureData>> featureData =
                eccoNode.getChildren().stream()
                        .filter(ch -> ch.getArtifact().getData() instanceof StructuralFeatureData)
                        .collect(
                                Collectors.groupingBy(
                                        c->((StructuralFeatureData)c.getArtifact().getData()).getFeatureId(),
                                        Collectors.mapping(c->(StructuralFeatureData) c.getArtifact().getData(),
                                                Collectors.toList())
                                )
                        );
        List<List<StructuralFeatureData>> features = new ArrayList<>(featureData.size());
        // Add a comparator to sort the features, first by attribute/feature, then name
        features.addAll(featureData.values());
        Collections.sort(features, new Comparator<List<StructuralFeatureData>>() {
            @Override
            public int compare(List<StructuralFeatureData> o1, List<StructuralFeatureData> o2) {
                Class<? extends StructuralFeatureData> class1 = o1.get(0).getClass();
                Class<? extends StructuralFeatureData> class2 = o2.get(0).getClass();
                if (class1 == class2) {
                    return class1.getName().compareToIgnoreCase(class2.getName());
                }
                if (class1.getName().contains("Attribute") && class2.getName().contains("Reference")) {
                    return -1;
                }
                if (class1.getName().contains("Reference") && class2.getName().contains("Attribute")) {
                    return 1;
                }
                if (class1.getName().contains("Single") && class2.getName().contains("Multi")) {
                    return -1;
                }
                return 1;
            }
        });
        isAmbiguous = false;
        for (List<StructuralFeatureData> entry : features) {
            Class dataClass = entry.get(0).getClass();
            if (entry.size() > 1) {      // Ambiguous
                BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode> factory = dispatchAmbiguous.get(dataClass);
                factory.apply(entry, node);
                isAmbiguous = true;
            }
            else {
                BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> factory = dispatch.get(dataClass);
                factory.apply(entry.get(0), node);
            }
        }

    }

    private static final Map<Class, BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode>> dispatch =  new HashMap<>();

    private static BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> createEObjectNode =
            (artifactData, parent) -> {
                assert artifactData instanceof EObjectArtifactData;
                EmfTreeNode node = new EmfTreeNode.EObjectNode(parent, (EObjectArtifactData) artifactData);
                return node;
            };

    private static BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> createEContainerNode =
            (artifactData, parent) -> {
                assert artifactData instanceof EContainerData;
                EmfTreeNode node = new EmfTreeNode.EContainerNode(parent, (EContainerData) artifactData);
                return node;
            };

    private static BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> createSingleAttributeNode =
            (artifactData, parent) -> {
                assert artifactData instanceof SinglevalueAttributeData;
                EmfTreeNode node = new EmfTreeNode.SingleAttributeNode(parent, (SinglevalueAttributeData) artifactData);
                return node;
            };

    private static BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> createMultiAttributeNode =
            (artifactData, parent) -> {
                assert artifactData instanceof MultivalueAttributteData;
                EmfTreeNode node = new EmfTreeNode.MultiAttributeNode(parent, (MultivalueAttributteData) artifactData);
                return node;
            };

    private static BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> createSingleReferenceNode =
            (artifactData, parent) -> {
                assert artifactData instanceof SinglevalueReferenceData;
                EmfTreeNode node = new EmfTreeNode.SingleReferenceNode(parent, (SinglevalueReferenceData) artifactData);
                return node;
            };

    private static BiFunction<ArtifactData, EmfTreeNode, EmfTreeNode> createMultiReferenceNode =
            (artifactData, parent) -> {
                assert artifactData instanceof MultivalueReferenceData;
                EmfTreeNode node = new EmfTreeNode.MultiReferenceNode(parent, (MultivalueReferenceData) artifactData);
                return node;
            };

    private static final Map<Class, BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode>> dispatchAmbiguous =
            new HashMap<>();

    private static BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode> createAmbiguousEContainerNode =
            (artifactData, parent) -> {
                EmfTreeNode node = new EmfTreeNode.AmbiguousEContainerNode(parent, (List<EContainerData>) artifactData);
                return node;
            };

    private static BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode> createAmbiguousSingleAttributeNode =
            (artifactData, parent) -> {
                EmfTreeNode node = new EmfTreeNode.AmbiguousSingleAttributeNode(parent, (List<SinglevalueAttributeData>) artifactData);
                return node;
            };

    private static BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode> createAmbiguousMultiAttributeNode =
            (artifactData, parent) -> {
                EmfTreeNode node = new EmfTreeNode.AmbiguousMultiAtributeNode(parent, (List<MultivalueAttributteData>) artifactData);
                return node;
            };

    private static BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode> createAmbiguousSingleReferenceNode =
            (artifactData, parent) -> {
                EmfTreeNode node = new EmfTreeNode.AmbiguousSingleReferenceNode(parent, (List<SinglevalueReferenceData>) artifactData);
                return node;
            };

    private static BiFunction<List<? extends ArtifactData>, EmfTreeNode, EmfTreeNode> createAmbiguousMultiReferenceNode =
            (artifactData, parent) -> {
                EmfTreeNode node = new EmfTreeNode.AmbiguousMultiRefernceNode(parent, (List<MultivalueReferenceData>) artifactData);
                return node;
            };


    static {
        dispatch.put(EObjectArtifactData.class, createEObjectNode);
        dispatch.put(EContainerData.class, createEContainerNode);
        dispatch.put(SinglevalueAttributeData.class, createSingleAttributeNode);
        dispatch.put(MultivalueAttributteData.class, createMultiAttributeNode);
        dispatch.put(SinglevalueReferenceData.class, createSingleReferenceNode);
        dispatch.put(MultivalueReferenceData.class, createMultiReferenceNode);
        dispatchAmbiguous.put(EContainerData.class, createAmbiguousEContainerNode);
        dispatchAmbiguous.put(SinglevalueAttributeData.class, createAmbiguousSingleAttributeNode);
        dispatchAmbiguous.put(MultivalueAttributteData.class, createAmbiguousMultiAttributeNode);
        dispatchAmbiguous.put(SinglevalueReferenceData.class, createAmbiguousSingleReferenceNode);
        dispatchAmbiguous.put(MultivalueReferenceData.class, createAmbiguousMultiReferenceNode);
    }

}
