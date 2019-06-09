package at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a node in graph grammar representation
 *
 * @author Michael Jahn
 */
public class Node {

    private String label;

    private List<ChildRelation> children;
    private transient List<Node> parents;
    private ChildRelation recursiveNode;

    private String content;

    public Node() {
        int i = 0;
    }


    public Node(String label) {
        this.label = label;

        children = new ArrayList<>();
        parents = new ArrayList<>();
        content = "";
    }

    public List<ChildRelation> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void setChildren(List<ChildRelation> children) {
        this.children = children;
        for (ChildRelation childRelation : this.children) {
            childRelation.getChildNode().parents = new ArrayList<>(Arrays.asList(this));
        }
    }

    public List<Node> getParents() {
        return Collections.unmodifiableList(parents);
    }

    public void addChild(ChildRelation node) {
        children.add(node);
        node.getChildNode().parents.add(this);
    }

    public void addParent(Node parentNode) {
        parentNode.children.add(new ChildRelation(this));
        parents.add(parentNode);
    }

    public void removeChild(ChildRelation childRelation) {
        children.remove(childRelation);
        childRelation.getChildNode().parents.remove(this);
    }

    public void removeParent(Node parentNode) {
        parents.remove(parentNode);
        parentNode.children.remove(this);
    }

    public String getLabel() {
        return label;
    }

    public boolean containsChild(Node node) {
        return children.contains(node);
    }

    public boolean hasParent(Node parent) {
        return parents.contains(parent);
    }

    public boolean isTerminalNode() {
        return false;
    }

    public boolean isNonTerminalNode() {
        return !isTerminalNode();
    }

    public String subTreeToString() {
        return subTreeToString("", new HashSet<String>());
    }

    public String subTreeToString(int maxDepth) {
        return subTreeToString("", maxDepth);
    }

    public List<Node> getConnectedNodes() {
        List<Node> connectedNodes = new ArrayList<>(parents);
        connectedNodes.addAll(children.stream().map(childRelation -> childRelation.getChildNode()).collect(Collectors.toList()));
        return Collections.unmodifiableList(connectedNodes);
    }

    protected String subTreeToString(String indention, Set<String> printedLabels) {
        if(!printedLabels.contains(this.getLabel())) {
            StringBuilder childrenString = new StringBuilder();
            for (ChildRelation childRelation : getChildren()) {
                childrenString.append(childRelation.subTreeToString(indention + "  ", new HashSet<>()));
            }
            printedLabels.add(this.getLabel());
            return indention + "- " + getLabel() + "\n" + childrenString;
        } else {
            return "";
        }
    }

    protected String subTreeToString(String indention, int maxDepth) {
        StringBuilder childrenString = new StringBuilder("");
        if (maxDepth > 0) {
            maxDepth--;
            for (ChildRelation childRelation : getChildren()) {
                childrenString.append(childRelation.subTreeToString(indention + "  ", maxDepth));
            }
        }
        return indention + "- " + getLabel() + "\n" + childrenString;

    }

    public Node getFirstChildPerLabelRecursive(String label) {
        return getFirstChildPerLabelRecursiveInternal(label, new HashSet<>());
    }

    private Node getFirstChildPerLabelRecursiveInternal(String label, Set<String> alreadyProcessed) {
        alreadyProcessed.add(this.getLabel());
        for (ChildRelation childRelation : children) {
            if(childRelation.getLabel().equals(label)) {
                return childRelation.getChildNode();
            }
        }
        Node result;
        for (ChildRelation childRelation : children) {
            if(!alreadyProcessed.contains(childRelation.getLabel())) {
                result = childRelation.getChildNode().getFirstChildPerLabelRecursiveInternal(label, alreadyProcessed);
                alreadyProcessed.add(childRelation.getLabel());
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public List<Node> getAllChildNodesPerLabelRecursive(String label) {
      return getAllChildNodesPerLabelRecursiveInternal(label, new HashSet<>());
    }

    public List<Node> getAllChildNodesPerLabelRecursiveInternal(String label, Set<Node> alreadyProcessed) {
        alreadyProcessed.add(this);
        List<Node> nodes = new ArrayList<>();
        for (ChildRelation childRelation : getChildren()) {
            if(!alreadyProcessed.contains(childRelation)) {
                if (childRelation.getLabel().equals(label)) {
                    nodes.add(childRelation.getChildNode());
                }
                nodes.addAll(childRelation.getChildNode().getAllChildNodesPerLabelRecursiveInternal(label, alreadyProcessed));
                alreadyProcessed.add(childRelation.getChildNode());
            }
        }
        return nodes;
    }


    public List<Node> getAllChildrenPerLabel(String label) {
        List<Node> nodes = new ArrayList<>();
        for (ChildRelation childRelation : getChildren()) {
            if (childRelation.getLabel().equals(label)) {
                nodes.add(childRelation.getChildNode());
            }
        }
        return nodes;
    }

    public List<ChildRelation> getAllChildRelationsPerLabel(String label) {
        List<ChildRelation> relations = new ArrayList<>();
        for (ChildRelation childRelation : getChildren()) {
            if (childRelation.getLabel().equals(label)) {
                relations.add(childRelation);
            }
        }
        return relations;
    }


    public Set<String> getAllChildLabels() {
        Set<String> labels = new HashSet<>();
        for (ChildRelation childRelation : children) {
            labels.add(childRelation.getLabel());
        }
        return labels;
    }

    public Set<String> getAllLabelsRecurisve() {
        return getAllLabelsRecursiveInternal(new HashSet<>());
    }

    private Set<String> getAllLabelsRecursiveInternal(Set<String> labels) {
        for (ChildRelation childRelation : children) {
            if(!labels.contains(childRelation.getLabel())) {
                labels.add(childRelation.getLabel());
                childRelation.getChildNode().getAllLabelsRecursiveInternal(labels);
            }
        }
        return labels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        if (label != null ? !label.equals(node.label) : node.label != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = label != null ? label.hashCode() : 0;
        return result;
    }

    public void setRecursiveNode(ChildRelation recursiveNodeRelation) {
        this.recursiveNode = recursiveNodeRelation;
    }

    public boolean isRecursiveNode() {
        return recursiveNode != null;
    }

    public ChildRelation getRecursiveNodeRelation() {
        return recursiveNode;
    }

    public void appendContent(String content) {
        this.content += content != null ? content : "";
    }

    public String getContent() {
        return content;
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty() && !org.apache.commons.lang3.StringUtils.isWhitespace(content);
    }
}
