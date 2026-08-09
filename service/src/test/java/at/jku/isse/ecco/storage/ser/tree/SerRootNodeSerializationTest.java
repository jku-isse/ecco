package at.jku.isse.ecco.storage.ser.tree;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SerRootNode.writeObject() never refreshed the root's own numberOfChildren before
 * out.defaultWriteObject() serialized it -- unlike every other node in the tree, refreshed by the
 * breadth-first loop's node.updateNumberOfChildren() right before each is written. SerNode.
 * removeChild() doesn't keep numberOfChildren in sync (only addChild()/addChildren() do), so a root
 * whose direct child was removed via removeChild() and then serialized would write the correct
 * (smaller) number of actual child objects but a stale (larger) numberOfChildren field, desyncing
 * readObject()'s reconstruction (it trusts numberOfChildren to know how many child objects to read).
 */
public class SerRootNodeSerializationTest {

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
	public void serializationSurvivesARootChildBeingRemoved() throws Exception {
		SerEntityFactory ef = new SerEntityFactory();
		RootNode.Op root = ef.createRootNode();
		Node.Op childA = ef.createNode(new TestData("A"));
		Node.Op childB = ef.createNode(new TestData("B"));
		root.addChild(childA);
		root.addChild(childB);

		root.removeChild(childB);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(root);
		}

		RootNode.Op reloaded;
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			reloaded = (RootNode.Op) in.readObject();
		}

		assertEquals(1, reloaded.getChildren().size(),
				"the root's child count after removeChild() must survive a serialize/deserialize round trip");
	}
}
