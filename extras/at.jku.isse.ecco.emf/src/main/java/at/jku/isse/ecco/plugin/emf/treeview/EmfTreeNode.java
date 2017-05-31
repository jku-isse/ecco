package at.jku.isse.ecco.plugin.emf.treeview;

/**
 * Created by hhoyos on 31/05/2017.
 */

import at.jku.isse.ecco.plugin.emf.util.ReflectiveItemProvider;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.*;

/**
 * An class to wrap items in the EmfTreeView
 */
public interface EmfTreeNode {

    /** An EMF item is a leaf if it is an EAttribute, or if it's EClass does not have any EStructuralFeatures  */
    boolean isLeaf();

    String getLabel();

    boolean isEmpty();

    class EObjectNode implements EmfTreeNode {

        private final EObject eObject;

        public EObjectNode(EObject eObject) {
            this.eObject = eObject;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public String getLabel() {
            EClass eClass = eObject.eClass();
            String label = String.format("[%s]", ReflectiveItemProvider.format(
                    ReflectiveItemProvider.capName(eClass.getName()), ' '));
            EStructuralFeature feature = ReflectiveItemProvider.getLabelFeature(eClass);
            if (feature != null) {
                Object value = eObject.eGet(feature);
                if (value != null) {
                    return label + " " + value.toString();
                }
            }
            return label;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        public EObject getEObject() {
            return eObject;
        }
    }

    abstract class EStructuralFeatureNode implements EmfTreeNode {

        protected final EStructuralFeature feature;
        private final EObject owner;

        public EStructuralFeatureNode(EStructuralFeature feature, EObject owner) {
            this.feature = feature;
            this.owner = owner;
        }

        public EStructuralFeature getFeature() {
            return feature;
        }

        public String getFeatureName() {
            return feature.getName();
        }

        public Object getValue() {
            return owner.eGet(feature);
        }
    }

    class SingleFeatureNode extends EStructuralFeatureNode {

        public SingleFeatureNode(EStructuralFeature feature, EObject owner) {
            super(feature, owner);
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            return String.format("%s = %s", getFeatureName(), getValue());
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    class MultiFeatureNode extends EStructuralFeatureNode {


        public MultiFeatureNode(EStructuralFeature feature, EObject owner) {
            super(feature, owner);
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public String getLabel() {
            int count = ((EList<Object>)getValue()).size();
            if (feature instanceof EReference) {
                return String.format("%s (%d)", getFeatureName(), count);
            }
            else {
                return String.format("%s (%d) = ", getFeatureName(), count);
            }
        }

        @Override
        public boolean isEmpty() {
            return ((EList<Object>)getValue()).isEmpty();
        }
    }

    /**
     * We want SingleReferences to show like a multivalue, where the children is an EObjectNode
     */
    class SingleReferenceNode extends MultiFeatureNode {

        public SingleReferenceNode(EStructuralFeature feature, EObject owner) {
            super(feature, owner);
        }

        @Override
        public String getLabel() {
            Object value = getValue();
            return String.format("%s (%d)", getFeatureName(), value == null ? 0 : 1);
        }

    }

    class PrimitiveValueNode implements EmfTreeNode {

        private final Object value;

        public PrimitiveValueNode(Object value) {
            this.value = value;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public String getLabel() {
            return value.toString();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
