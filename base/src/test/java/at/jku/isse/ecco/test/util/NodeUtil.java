package at.jku.isse.ecco.test.util;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.test.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

public class NodeUtil {

    /**
     * root
     * -00
     * --10
     * --11
     * ---oa0
     * ----atomic0
     * ----atomic1
     * -----atomic3
     * -----atomic5
     * ------atomic6
     * ----atomic2
     * ---oa1
     * ---oa2
     * ---oa3
     * ---oa4
     * ---oa5
     * -01
     *
     * @return Root of the tree.
     */
    public static Node.Op createTestTree1() {
        EntityFactory ef = new MemEntityFactory();

        RootNode.Op root = ef.createRootNode();

        // first level
        Node.Op n00 = ef.createNode(new TestArtifactData("00"));
        Node.Op n01 = ef.createNode(new TestArtifactData("01"));
        root.addChild(n00);
        root.addChild(n01);

        // second level
        Node.Op n10 = ef.createNode(new TestArtifactData("10"));
        Node.Op n11 = ef.createOrderedNode(new TestArtifactData("11"));
        n00.addChild(n10);
        n00.addChild(n11);

        // third level
        Node.Op n20 = ef.createNode(new TestArtifactData("oa0"));
        n20.getArtifact().setAtomic(true);
        Node.Op n21 = ef.createNode(new TestArtifactData("oa1"));
        Node.Op n22 = ef.createNode(new TestArtifactData("oa2"));
        Node.Op n23 = ef.createNode(new TestArtifactData("oa3"));
        Node.Op n25 = ef.createNode(new TestArtifactData("oa5"));
        n11.addChildren(n20, n21, n22, n23, n25);

        // fourth level
        Node.Op n30 = ef.createNode(new TestArtifactData("atomic0"));
        Node.Op n31 = ef.createNode(new TestArtifactData("atomic1"));
        Node.Op n32 = ef.createNode(new TestArtifactData("atomic2"));
        n20.addChildren(n30, n31, n32);

        // fifth level
        Node.Op n40 = ef.createNode(new TestArtifactData("atomic3"));
        Node.Op n41 = ef.createNode(new TestArtifactData("atomic4"));
        Node.Op n42 = ef.createNode(new TestArtifactData("atomic5"));
        n31.addChildren(n40, n41, n42);

        // 6th level
        Node.Op n50 = ef.createNode(new TestArtifactData("atomic6"));
        n42.addChild(n50);

        return root;
    }

    public static Node.Op createTestTree2() {
        EntityFactory ef = new MemEntityFactory();

        RootNode.Op root = ef.createRootNode();

        // first level
        Node.Op n00 = ef.createNode(new TestArtifactData("00"));
        Node.Op n02 = ef.createNode(new TestArtifactData("02"));
        root.addChild(n00);
        root.addChild(n02);

        // second level
        Node.Op n10 = ef.createNode(new TestArtifactData("10"));
        Node.Op n11 = ef.createOrderedNode(new TestArtifactData("11"));
        n00.addChild(n10);
        n00.addChild(n11);

        // third level
        Node.Op n20 = ef.createNode(new TestArtifactData("oa0"));
        n20.getArtifact().setAtomic(true);
        Node.Op n21 = ef.createNode(new TestArtifactData("oa1"));
        Node.Op n26 = ef.createNode(new TestArtifactData("oa6"));
        Node.Op n23 = ef.createNode(new TestArtifactData("oa3"));
        Node.Op n24 = ef.createNode(new TestArtifactData("oa4"));
        Node.Op n25 = ef.createNode(new TestArtifactData("oa5"));
        n11.addChildren(n20, n21, n26, n23, n24, n25);

        // fourth level
        Node.Op n30 = ef.createNode(new TestArtifactData("atomic0"));
        Node.Op n31 = ef.createNode(new TestArtifactData("atomic1"));
        Node.Op n32 = ef.createNode(new TestArtifactData("atomic2"));
        n20.addChildren(n30, n31, n32);

        // fifth level
        Node.Op n40 = ef.createNode(new TestArtifactData("atomic3"));
        Node.Op n41 = ef.createNode(new TestArtifactData("atomic4"));
        Node.Op n42 = ef.createNode(new TestArtifactData("atomic5"));
        n31.addChildren(n40, n41, n42);

        // 6th level
        Node.Op n50 = ef.createNode(new TestArtifactData("atomic6"));
        n42.addChild(n50);

        return root;
    }
}
