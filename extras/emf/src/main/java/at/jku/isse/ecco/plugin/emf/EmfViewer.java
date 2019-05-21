package at.jku.isse.ecco.plugin.emf;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.emf.data.EmfResourceData;
import at.jku.isse.ecco.plugin.emf.treeview.AmbiguousEmfTree;
import at.jku.isse.ecco.plugin.emf.treeview.EmfTreeCellImpl;
import at.jku.isse.ecco.plugin.emf.treeview.EmfTreeItem;
import at.jku.isse.ecco.plugin.emf.treeview.EmfTreeNode;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import javax.inject.Inject;
import java.util.*;

/**
 * Created by hhoyos on 23/05/2017.
 */
public class EmfViewer extends BorderPane implements ArtifactViewer {

    private TreeView<EmfTreeNode> treeView;
    private AmbiguousEmfTree emfTree;

    @Inject
    public EmfViewer(ResourceSet resourceSet) {
        this.getStylesheets().add("emf-plugin.css");
    }

    @Override
    public String getPluginId() {
        return EmfPlugin.class.getName();
    }

    @Override
    public void showTree(Node node) {
        ArtifactData data = node.getArtifact().getData();
        // Initial tree nodes must be ignored
        if (data instanceof PluginArtifactData) {
            return;
        }
        if (emfTree == null) {
            Node rootnode = node;
            while (!(data instanceof EmfResourceData)) {
                rootnode = node.getParent();
                data = rootnode.getArtifact().getData();
            }
            emfTree = new AmbiguousEmfTree((Node.Op) rootnode);
            TreeItem<EmfTreeNode> dummyRoot = new TreeItem<>();
            treeView = new TreeView<>(dummyRoot);
            for (EmfTreeNode treenode : emfTree.getChildren()) {
                TreeItem<EmfTreeNode> item = new EmfTreeItem(treenode);
                dummyRoot.getChildren().add(item);
            }
            if (emfTree.isAmbiguous()) {
                // Add a label to the root so it telss it is ambiguous!
            }
            else {
                treeView.setShowRoot(false);
            }
            // Cell factory
            treeView.setCellFactory(tree -> new EmfTreeCellImpl());
        }
        this.setCenter(treeView);
        MultipleSelectionModel msm = treeView.getSelectionModel();
        TreeItem<EmfTreeNode> treeItem;
        if (node.getArtifact().getData() instanceof EmfResourceData) {  // If the node is for an EmfResourceData artifact, show from the root
            treeItem = treeView.getRoot();
        }
        else {      // if (node.getArtifact().getData() instanceof EmfResourceData)
//            // FIXME They can click an attribute too
//            EObject nodeEObject = rc.getEObjectForNode((Node.Op) node);
//            List<EObject> branches = new ArrayList<>();
//            branches.add(nodeEObject);
//            // List the ancestors till the root
//            EObject eContainer = nodeEObject.eContainer();
//            while (eContainer != null) {
//                if (!(eContainer instanceof AmbiguousEObject)) {
//                    eContainer = AmbiguousEObject.wrap(eContainer);
//                }
//                branches.add(eContainer);
//                eContainer = eContainer.eContainer();
//            }
//            // Expand ancestors
//            Collections.reverse(branches);
//            treeItem = expandBranches(branches);
        }
//        int row = treeView.getRow( treeItem );
//        // Now the row can be selected.
//        msm.select( row );
    }

//    private TreeItem<EmfTreeNode> expandBranches(List<EObject> branches) {
//        TreeItem<EmfTreeNode> item = treeView.getRoot();
//        item.setExpanded(true);
//        for (int i = 0; i < branches.size(); i++) {
//            EObject eObject = branches.get(i);
//            // Find the child with the next item, and expand
//            item = findTreeItemForEObject(eObject, item);
//        }
//        return item;
//    }
//
//    private TreeItem<EmfTreeNode> findTreeItemForEObject(EObject eObject, TreeItem<EmfTreeNode> item) {
//        TreeItem<EmfTreeNode> founditem = null;
//        if(!item.isLeaf()){
//            for(TreeItem<EmfTreeNode> child : item.getChildren()){
//                // FIXME We would need to search inside each of the children children looking for reference nodes, and
//                // then for EObject nodes... Is ti worth it? SInce the artifact tree is not really a tree that
//                // reresents the resouce I dont see why people would want to dig into it too much... still makes thinkgs work nicer
//                Object childNode = child.getValue();
//                if (childNode instanceof EmfTreeNode.EObjectNode) {
//                    EmfTreeNode.EObjectNode eNode = (EmfTreeNode.EObjectNode) childNode;
//                    if (eObject.equals(eNode.getEObject())) {
//                        founditem =  child;
//                        item.setExpanded(true);
//                    }
//                }
//                else if (childNode instanceof EmfTreeNode.MultiFeatureNode) {
//                    EmfTreeNode.MultiFeatureNode multiFeatureNode = (EmfTreeNode.MultiFeatureNode) childNode;
//                    EStructuralFeature feature = multiFeatureNode.getFeature();
//                    if (feature instanceof EReference) {
//                        if (((EReference)feature).isContainment()) {
//                            founditem = findTreeItemForEObject(eObject, child);
//                        }
//                    }
//                }
//                if (founditem != null) {
//                    child.setExpanded(true);
//                    break;
//                }
//            }
//        }
//        return founditem;
//    }
}
