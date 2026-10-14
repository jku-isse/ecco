package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.*;
import at.jku.isse.ecco.pog.*;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PartialOrderGraphTest {

	@Test
	public void mergingWithBranchesWorks() {
		List<Artifact.Op<?>> thisArtifacts1 = Arrays.asList(A("1"), A("3"), A("4"), A("5"));
		List<Artifact.Op<?>> thisArtifacts2 = Arrays.asList(A("1"), A("2"), A("4"), A("5"));
		PartialOrderGraph.Op thisPog = new SerPartialOrderGraph();
		thisPog.merge(thisArtifacts1);
		thisPog.merge(thisArtifacts2);

		List<Artifact.Op<?>> otherArtifacts = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"));
		PartialOrderGraph.Op otherPog = new SerPartialOrderGraph();
		otherPog.merge(otherArtifacts);

		thisPog.merge(otherPog);
		PartialOrderGraph.Node.Op head = thisPog.getHead();
		PartialOrderGraph.Node.Op node;

		assertEquals(1, head.getNext().size());
		node = head.getNext().iterator().next();
		assertEquals(A("1"), node.getArtifact());

		assertEquals(1, node.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("2"), node.getArtifact());

		assertEquals(1, node.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("3"), node.getArtifact());

		assertEquals(1, node.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("4"), node.getArtifact());

		assertEquals(1, node.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("5"), node.getArtifact());

		assertEquals(1, node.getNext().size());
		assertEquals(0, node.getNext().iterator().next().getNext().size());
	}

	@Test
	public void mergeTest() {
		List<Artifact.Op<?>> thisArtifacts1 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"));
		List<Artifact.Op<?>> thisArtifacts2 = Arrays.asList(A("1"), A("3"), A("2"), A("4"), A("5"));
		PartialOrderGraph.Op thisPog = new SerPartialOrderGraph();
		thisPog.merge(thisArtifacts1);
		thisPog.merge(thisArtifacts2);

		PartialOrderGraph.Node.Op head = thisPog.getHead();
		PartialOrderGraph.Node.Op node;

		assertEquals(1, head.getNext().size());
		node = head.getNext().iterator().next();
		assertEquals(A("1"), node.getArtifact());

		assertEquals(1, head.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("2"), node.getArtifact());

		assertEquals(1, head.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("3"), node.getArtifact());

		assertEquals(1, head.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("2"), node.getArtifact());

		assertEquals(1, head.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("4"), node.getArtifact());

		assertEquals(1, head.getNext().size());
		node = node.getNext().iterator().next();
		assertEquals(A("5"), node.getArtifact());
	}


	/**
	 * Convenience method for creating artifacts.
	 *
	 * @param id The identifier of the artifact.
	 * @return The created artifact.
	 */
	private Artifact.Op<?> A(String id) {
		return new SerArtifact<>(new TestArtifactData(id));
	}

}

