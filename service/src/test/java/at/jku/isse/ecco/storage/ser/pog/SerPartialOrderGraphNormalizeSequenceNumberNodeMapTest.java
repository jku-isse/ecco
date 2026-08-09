package at.jku.isse.ecco.storage.ser.pog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * sequenceNumberNodeMap is a non-transient field, so a .ecco repository serialized by a version of
 * this class from before the field existed would deserialize it as null (Java leaves an absent
 * field at its default) - readObject() used to check for that null case (a comment right above it
 * even documents the intent) but then immediately dereferenced the same field unconditionally right
 * after ("for (SerPartialOrderGraphNode node : this.sequenceNumberNodeMap.values())"), throwing a
 * NullPointerException instead of falling back the way the check clearly intended.
 * <p>
 * Testing this via a real end-to-end Java serialization round trip isn't practical: writeObject()
 * always assigns a real (possibly empty) map before writing, so there's no way to produce a stream
 * missing the field without a second, differently-shaped compiled class under a separate
 * classloader. Since normalizeSequenceNumberNodeMap() is the entire fix in one self-contained,
 * side-effect-free method, this tests it directly via reflection instead.
 */
public class SerPartialOrderGraphNormalizeSequenceNumberNodeMapTest {

	@Test
	@Timeout(30)
	@SuppressWarnings("unchecked")
	public void normalizesANullMapToAnEmptyOneInsteadOfLeavingItNull() throws Exception {
		SerPartialOrderGraph graph = new SerPartialOrderGraph();

		Field mapField = SerPartialOrderGraph.class.getDeclaredField("sequenceNumberNodeMap");
		mapField.setAccessible(true);
		mapField.set(graph, null); // simulate the post-defaultReadObject() state for a pre-field stream

		Method normalize = SerPartialOrderGraph.class.getDeclaredMethod("normalizeSequenceNumberNodeMap");
		normalize.setAccessible(true);
		normalize.invoke(graph);

		Map<Integer, SerPartialOrderGraphNode> map = (Map<Integer, SerPartialOrderGraphNode>) mapField.get(graph);
		assertNotNull(map, "a null map must be normalized to a non-null one, not left null");
		assertTrue(map.isEmpty());
	}
}
