package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link LilypondStringWriter} mirrors {@link LilypondWriter} but joins tokens into a String
 * instead of writing a file - it shares {@link LilypondWriter#collectTokenNodes}, already covered
 * in depth by {@link LilypondWriterTest}, so this only needs to confirm the String-specific path
 * itself produces the same joined text. */
public class LilypondStringWriterTest {

	private final SerEntityFactory ef = new SerEntityFactory();
	private final LilypondStringWriter writer = new LilypondStringWriter();

	@Test
	void writeJoinsTokensWithSpacesIntoAString() {
		Node.Op fileNode = ef.createOrderedNode(ef.createArtifact(new PluginArtifactData(writer.getPluginId(), Path.of("song.ly"))));
		fileNode.addChild(ef.createOrderedNode(ef.createArtifact(new DefaultTokenArtifactData(new ParceToken(0, "a", "Token")))));
		fileNode.addChild(ef.createOrderedNode(ef.createArtifact(new DefaultTokenArtifactData(new ParceToken(1, "b", "Token")))));

		Set<Node> input = new HashSet<>();
		input.add(fileNode);
		String[] result = writer.write(input);

		assertEquals(1, result.length);
		assertEquals("a b", result[0]);
	}
}
