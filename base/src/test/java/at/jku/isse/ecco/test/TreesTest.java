package at.jku.isse.ecco.test;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Trees;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class TreesTest {

	@Test(groups = {"unit", "base", "tree"})
	public void Trees_Full() {
		EntityFactory ef = new MemEntityFactory();

		Node.Op root1 = this.createTestTree1();
		Node.Op root2 = this.createTestTree2();

		Trees.checkConsistency(root1); // TODO: have this return true/false or an error code instead of throwing an exception?
		Trees.checkConsistency(root2);

		Assert.assertEquals(Trees.countArtifacts(root1), 16);
		Assert.assertEquals(Trees.countArtifacts(root2), 17);

		Trees.print(root1);
		Trees.print(root2);

		Trees.updateArtifactReferences(root1);
		Trees.updateArtifactReferences(root2);

		Trees.map(root1, root2);

		//Node.Op copy1 = EccoUtil.deepCopyTree(root1, ef);

		Node.Op root3 = Trees.slice(root1, root2);

		Trees.sequence(root1);
		Trees.sequence(root2);
		Trees.sequence(root3);

		//NodeOperator.NodeOperand merge1 = Trees.merge(root1, root3); // TODO: should it be like this?

		//Trees.merge(root1, root3);
		root1.merge(root3);

		//Trees.equals(root1, copy1);
		//root1.merge(copy1); // TODO: this fails... why?


		// TODO: other operations

	}


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
	private Node.Op createTestTree1() {
		EntityFactory ef = new MemEntityFactory();

		RootNode.Op root = ef.createRootNode();

		// first level
		Node.Op n00 = ef.createNode(new TestArtifactData("00"));
		Node.Op n01 = ef.createNode(new TestArtifactData("01"));
		root.addChild(n00);
		root.addChild(n01);

		// TODO: make nodeOperator.NodeOperand.getChildren() read only and use addChild and removeChild for manipulating children? this can also make sure that parent pointers are always set correctly.
		// TODO: the "unique" (i.e. "solid") property is redundant. determine "uniqueness" via "nodeOperator.NodeOperand.getArtifact().getContainingNode() == nodeOperator.NodeOperand".
		// TODO: make sure that in non-ordered nodes artifacts that are equal cannot be added multiple times! add that check to "Trees.checkConsistency()".

		// second level
		Node.Op n10 = ef.createNode(new TestArtifactData("10"));
		Node.Op n11 = ef.createOrderedNode(new TestArtifactData("11"));
		n00.addChild(n10);
		n00.addChild(n11);

		// TODO: should there also be a cycle check? either in "Trees.checkConsistency()" or during "addChild"? actually not needed if the addChild method not only "corrects" the parent but also removes the child from the old parent.

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

		// TODO: children of atomic nodes are always treated as atomic, even if they themselves are noted marked as such (via "setAtomic(true)").

		// 6th level
		Node.Op n50 = ef.createNode(new TestArtifactData("atomic6"));
		n42.addChild(n50);


		// TODO: add artifact references. test for dependency graphs. add reference tests to "Trees.checkConsistency()". the references not only need to be equal, but also "==".

		return root;
	}

	private Node.Op createTestTree2() {
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


	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
