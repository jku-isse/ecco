package at.jku.isse.ecco.plugin.emf.treeview;

/**
 * Created by hhoyos on 31/05/2017.
 */

import at.jku.isse.ecco.plugin.emf.data.*;
import at.jku.isse.ecco.plugin.emf.util.ReflectiveItemProvider;
import com.sun.istack.internal.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The EmfTreeNode is used to construct the AmbiguousEmfTree that is used by the viewer.
 */
public interface EmfTreeNode {

    /** An EMF item is a leaf if it is an EAttribute, or if it's EClass does not have any EStructuralFeatures  */
    boolean isLeaf();

    String getLabel();

    boolean isEmpty();

    EmfTreeNode getParent();

    List<EmfTreeNode> getChildren();

    /**
     * The BaseNode defines the parent-children structure of the tree
     */
    abstract class BaseNode implements EmfTreeNode {

        private final EmfTreeNode parent;
        private final List<EmfTreeNode> children;

        public BaseNode(@Nullable EmfTreeNode parent) {
            this.parent = parent;
            this.children = new ArrayList<>();
            if (parent != null) {
                parent.getChildren().add(this);
            }
        }

        @Override
        public EmfTreeNode getParent() {
            return parent;
        }

        @Override
        public List<EmfTreeNode> getChildren() {
            return children;
        }
    }

    interface AmbiguousEmfTreeNode extends EmfTreeNode {

        void setSelection(int selection);

        int getSelection();
    }

    /**
     * An EObjectNode represents an EObject
     */
    class EObjectNode extends BaseNode {

        public static Map<EObjectArtifactData, EObjectNode> trace = new HashMap<>();

        private final EObjectArtifactData data;

        public EObjectNode(EmfTreeNode parent, EObjectArtifactData data) {
            super(parent);
            this.data = data;
            trace.put(data, this);
        }

        public EObjectArtifactData getData() {
            return data;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public String getLabel() {
            String label = String.format("[%s]", ReflectiveItemProvider.format(
                    ReflectiveItemProvider.capName(data.geteClassName()), ' '));
            Object id = data.getId();
            if (id != null) {
                return label + " " + id.toString();
            }
            return label;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        // In the future this can be used to reconstruct the resource.
//        public EObject getEObject() {
//            return eObject;
//        }
    }

    class EContainerNode extends BaseNode {

        public EContainerNode(EmfTreeNode parent, EContainerData data) {
            super(parent);
            // There is a single child, the node for the EContainer.
            EObjectNode node = EObjectNode.trace.get(data.getContainer());
            getChildren().add(node);
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public String getLabel() {
            return "/eContainer";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    class AmbiguousEContainerNode extends BaseNode implements AmbiguousEmfTreeNode {

        private int selection = -1;

        public AmbiguousEContainerNode(EmfTreeNode parent, List<EContainerData> data) {
            super(parent);
            // There are multiple childs
            for (EContainerData datum : data) {
                EObjectNode node = EObjectNode.trace.get(datum.getContainer());
                getChildren().add(node);
            }
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            return String.format("eContainer \u2718");
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void setSelection(int selection) {
            this.selection = selection;
        }

        public int getSelection() {
            return selection;
        }
    }

    /**
     * A SingleAttributeNode represents an Attribute with a single value. If the attribute value is unset an x is used
     * as the value.
     */
    class SingleAttributeNode extends BaseNode {

        protected final SinglevalueAttributeData data;

        public SingleAttributeNode(EmfTreeNode parent, SinglevalueAttributeData data) {
            super(parent);
            this.data = data;
        }

        public Object getValue() {
            return data.getValue().getValue();
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            String value;
            if (data.isSet()) {
                value = data.getValue().getValue();
            }
            else {
                value = " \u2718";
            }
            return String.format("%s = %s", data.getFeatureName(), value);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /**
     * For ambiguous attributes there is no need for a nested structure. The ambiguities are displayed as text in the
     * attribute's label.
     */
    class AmbiguousSingleAttributeNode extends BaseNode implements AmbiguousEmfTreeNode {

        private final List<SinglevalueAttributeData> data;
        private int selection = -1;

        public AmbiguousSingleAttributeNode(EmfTreeNode parent, List<SinglevalueAttributeData> data) {
            super(parent);
            this.data = data;
            // There are multiple childs
            for (SinglevalueAttributeData datum : data) {
                EmfTreeNode node = new EmfTreeNode.SingleAttributeNode(this, datum);
            }
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            return String.format("%s \u2753", data.get(0).getFeatureName());
        }

//        @Override
//        public String getLabel() {
//            String value = data.stream()
//                    .map(d -> d.isSet() ? d.getValue().toString() : "\u2753" )
//                    .collect(Collectors.joining(" | "));
//            return String.format("%s = %s", data.get(0).getFeatureName(), value);
//        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void setSelection(int selection) {
            this.selection = selection;
        }

        public int getSelection() {
            return selection;
        }
    }

    /**
     * The label changes to represent the values as a Set "{...}" or as a Sequence "[...]"
     */
    class MultiAttributeNode extends BaseNode {

        protected final MultivalueAttributteData data;

        public MultiAttributeNode(EmfTreeNode parent, MultivalueAttributteData data) {
            super(parent);
            this.data = data;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            String value;
            String collection;
            if (data.isSet()) {
                collection = data.isUnique() ? "{%s}" : "[%s]";
                value = String.format(collection, data.getContents().stream()
                        .map(c -> c.toString())
                        .collect(Collectors.joining(", ")));
            }
            else {
                value = " \u2718";
            }
            return String.format("%s = %s", data.getFeatureName(), value);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /**
     * An AmbiguousMultiAtributeNode represents the ambiguities as nested elements. For each alternative values a
     * MultiAttributeNode child is added
     */
    class AmbiguousMultiAtributeNode extends BaseNode implements AmbiguousEmfTreeNode {

        private final String featureName;
        private int selection = -1;

        public AmbiguousMultiAtributeNode(EmfTreeNode parent, List<MultivalueAttributteData> data) {
            super(parent);
            this.featureName = data.get(0).getFeatureName();
            for (MultivalueAttributteData datum : data) {
                new MultiAttributeNode(this, datum);
            }
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            return String.format("%s (\u2753)", featureName);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void setSelection(int selection) {
            this.selection = selection;
        }

        public int getSelection() {
            return selection;
        }
    }

    /**
     * A MultiFeatureNode represents a Feature with multiple value. If the attribute value is unset an x is used
     * as the value. The feature values are nested as leafs/branches of the node.
     */
    class SingleReferenceNode extends BaseNode {

        private final SinglevalueReferenceData data;

        public SingleReferenceNode(EmfTreeNode parent, SinglevalueReferenceData data) {
            super(parent);
            this.data = data;
            // There is a single child, the node for the EObjectec referenced.
            EObjectNode node = EObjectNode.trace.get(data.getValue());
            getChildren().add(node);
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public String getLabel() {
            String value;
            if (data.isSet()) {
                value = String.format("%s (1)", data.getFeatureName());
            }
            else {
                value = String.format("%s  \u2718", data.getFeatureName());
            }
            return value;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /**
     * We want SingleReferences to show like a multivalue, where the children is an EObjectNode
     */
    class AmbiguousSingleReferenceNode extends BaseNode implements AmbiguousEmfTreeNode {

        private final List<SinglevalueReferenceData> data;
        private int selection = -1;

        public AmbiguousSingleReferenceNode(EmfTreeNode parent, List<SinglevalueReferenceData> data) {
            super(parent);
            this.data = data;
            // There are multiple childs
            for (SinglevalueReferenceData datum : data) {
                EObjectNode node = EObjectNode.trace.get(datum.getValue());
                getChildren().add(node);
            }
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            return String.format("%s \u2753", data.get(0).getFeatureName());
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void setSelection(int selection) {
            this.selection = selection;
        }

        public int getSelection() {
            return selection;
        }
    }

    class MultiReferenceNode extends BaseNode {

        private final MultivalueReferenceData data;

        public MultiReferenceNode(EmfTreeNode parent, MultivalueReferenceData data) {
            super(parent);
            this.data = data;
            for (Object datum : data.getContents()) {
                assert datum instanceof EObjectArtifactData;
                EObjectNode node = EObjectNode.trace.get(datum);
                getChildren().add(node);
            }
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public String getLabel() {
            String value;
            if (data.isSet()) {
                value = String.format("%s (%s)", data.getFeatureName(), data.getContents().size());
            }
            else {
                value = String.format("%s  \u2718", data.getFeatureName());
            }
            return value;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    class AmbiguousMultiRefernceNode extends BaseNode implements AmbiguousEmfTreeNode {

        private final String featureName;
        private int selection = -1;

        public AmbiguousMultiRefernceNode(EmfTreeNode parent, List<MultivalueReferenceData> data) {
            super(parent);
            this.featureName = data.get(0).getFeatureName();
            for (MultivalueReferenceData datum : data) {
                MultiReferenceNode node = new MultiReferenceNode(this, datum);
            }
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            return String.format("%s \u2753", featureName);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void setSelection(int selection) {
            this.selection = selection;
        }

        public int getSelection() {
            return selection;
        }
    }

}
