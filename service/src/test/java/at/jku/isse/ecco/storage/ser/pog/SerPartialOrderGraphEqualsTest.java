package at.jku.isse.ecco.storage.ser.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * SerPartialOrderGraph.equals() (via SerPartialOrderGraphNode.equalsCompletely()) used to always
 * return false for any two non-identical graph objects, no matter how structurally identical their
 * content -- it delegated to SerPartialOrderGraphNode's inherited (identity-based) equals(), which by
 * construction is already false past the "this == o" fast-path. Fixed by comparing artifacts directly
 * instead of relying on Node.Op#equals() (which stays identity-based deliberately -- see
 * PartialOrderGraph.nodeOccursSameNumberOfTimes()'s comment -- since Collection#contains()/#remove()
 * and HashMap keys elsewhere in trim()/merge()/addRelations() depend on it not treating
 * same-content-different-position nodes as interchangeable).
 */
public class SerPartialOrderGraphEqualsTest {

	static class TestData implements ArtifactData {
		private final String id;

		TestData(String id) {
			this.id = id;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof TestData && ((TestData) o).id.equals(id);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(id);
		}

		@Override
		public String toString() {
			return id;
		}
	}

	@Test
	@Timeout(30)
	public void independentlyBuiltGraphsWithEqualContentAreEqual() {
		List<Artifact.Op<?>> artifactsA = List.of(new SerArtifact<>(new TestData("A")), new SerArtifact<>(new TestData("B")));
		List<Artifact.Op<?>> artifactsB = List.of(new SerArtifact<>(new TestData("A")), new SerArtifact<>(new TestData("B")));

		PartialOrderGraph.Op pogA = new SerPartialOrderGraph().fromList(artifactsA);
		PartialOrderGraph.Op pogB = new SerPartialOrderGraph().fromList(artifactsB);

		assertNotSame(pogA, pogB, "the two graphs must be independently built, not sharing node/graph objects");
		assertEquals(pogA, pogB, "structurally identical, independently-built graphs must compare equal");
	}

	@Test
	@Timeout(30)
	public void graphsWithDifferentContentAreNotEqual() {
		List<Artifact.Op<?>> artifactsA = List.of(new SerArtifact<>(new TestData("A")), new SerArtifact<>(new TestData("B")));
		List<Artifact.Op<?>> artifactsC = List.of(new SerArtifact<>(new TestData("A")), new SerArtifact<>(new TestData("C")));

		PartialOrderGraph.Op pogA = new SerPartialOrderGraph().fromList(artifactsA);
		PartialOrderGraph.Op pogC = new SerPartialOrderGraph().fromList(artifactsC);

		assertNotEquals(pogA, pogC, "graphs with different content must not compare equal");
	}
}
