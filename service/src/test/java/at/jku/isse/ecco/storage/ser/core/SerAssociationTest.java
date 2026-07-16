package at.jku.isse.ecco.storage.ser.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code minimizedCondition} is an additive, nullable field (see its javadoc in
 * {@link SerAssociation}) added without bumping {@code serialVersionUID} -- an association
 * serialized before this field existed simply deserializes with it {@code null} ("never
 * minimized"), no migration needed. This pins down both ends of that contract: a freshly
 * constructed association (the "old data" shape, field never touched) round-trips as null, and an
 * association with the field set round-trips the value.
 */
public class SerAssociationTest {

	private static SerAssociation roundTrip(SerAssociation original) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(original);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			return (SerAssociation) in.readObject();
		}
	}

	@Test
	public void neverSet_deserializesAsNull() throws Exception {
		SerAssociation association = new SerAssociation();
		SerAssociation restored = roundTrip(association);
		assertNull(restored.getMinimizedCondition());
	}

	@Test
	public void set_roundTripsThroughSerialization() throws Exception {
		SerAssociation association = new SerAssociation();
		association.setMinimizedCondition("(A AND B) OR C");
		SerAssociation restored = roundTrip(association);
		assertEquals("(A AND B) OR C", restored.getMinimizedCondition());
	}

	@Test
	public void clearedAfterBeingSet_deserializesAsNull() throws Exception {
		SerAssociation association = new SerAssociation();
		association.setMinimizedCondition("A");
		association.setMinimizedCondition(null);
		SerAssociation restored = roundTrip(association);
		assertNull(restored.getMinimizedCondition());
	}
}
