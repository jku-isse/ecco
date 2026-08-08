package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.ContextArtifactDataFactory;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link LilypondWriter}: the {@code collectTokenNodes()} traversal it shares with
 * {@link LilypondStringWriter}, and end-to-end file writing including failure handling. */
public class LilypondWriterTest {

	private final SerEntityFactory ef = new SerEntityFactory();
	private final LilypondWriter writer = new LilypondWriter();

	private Node.Op token(String text) {
		return ef.createOrderedNode(ef.createArtifact(new DefaultTokenArtifactData(new ParceToken(0, text, "Token"))));
	}

	@Test
	void collectTokenNodesVisitsTokensInDocumentOrderIncludingATokenWithItsOwnChild() {
		Node.Op outer = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("outer")));
		Node.Op a = token("a");
		Node.Op b = token("b");
		Node.Op bNested = token("b-nested");
		Node.Op c = token("c");

		outer.addChild(a);
		outer.addChild(b);
		b.addChild(bNested); // a token node with its own child - the trickiest case: it must
		                      // still be visited itself AND recursed into
		outer.addChild(c);

		List<Node> collected = new ArrayList<>();
		LilypondWriter.collectTokenNodes(outer, collected);

		assertEquals(List.of(a, b, bNested, c), collected);
	}

	@Test
	void collectTokenNodesSkipsContextNodesButRecursesThroughThem() {
		Node.Op outer = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("outer")));
		Node.Op inner = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("inner")));
		Node.Op a = token("a");
		outer.addChild(inner);
		inner.addChild(a);

		List<Node> collected = new ArrayList<>();
		LilypondWriter.collectTokenNodes(outer, collected);

		assertEquals(List.of(a), collected);
	}

	@Test
	void writeJoinsTokensWithSpacesByDefault(@TempDir Path tempDir) throws IOException {
		Node.Op fileNode = ef.createOrderedNode(ef.createArtifact(new PluginArtifactData(writer.getPluginId(), Path.of("song.ly"))));
		fileNode.addChild(token("a"));
		fileNode.addChild(token("b"));
		fileNode.addChild(token("c"));

		Set<Node> input = new HashSet<>();
		input.add(fileNode);
		Path[] written = writer.write(tempDir, input);

		assertEquals(1, written.length);
		assertEquals("a b c", Files.readString(written[0]));
	}

	@Test
	void writeOfAFileWithNoTokensProducesAnEmptyFile(@TempDir Path tempDir) throws IOException {
		Node.Op fileNode = ef.createOrderedNode(ef.createArtifact(new PluginArtifactData(writer.getPluginId(), Path.of("empty.ly"))));

		Set<Node> input = new HashSet<>();
		input.add(fileNode);
		Path[] written = writer.write(tempDir, input);

		assertEquals("", Files.readString(written[0]));
	}

	@Test
	void writeRejectsANodeWithoutPluginArtifactData() {
		Node.Op notAFileNode = token("a");
		Set<Node> input = new HashSet<>();
		input.add(notAFileNode);

		assertThrows(EccoException.class, () -> writer.write(Path.of("."), input));
	}

	@Test
	void writeThrowsEccoExceptionInsteadOfSilentlySwallowingAnIOFailure(@TempDir Path tempDir) {
		// point the output path at a directory that doesn't exist and is never created, so the
		// write itself fails with a real IOException
		Node.Op fileNode = ef.createOrderedNode(ef.createArtifact(
				new PluginArtifactData(writer.getPluginId(), Path.of("no-such-dir/song.ly"))));
		fileNode.addChild(token("a"));

		Set<Node> input = new HashSet<>();
		input.add(fileNode);

		EccoException thrown = assertThrows(EccoException.class, () -> writer.write(tempDir, input));
		assertTrue(thrown.getCause() instanceof IOException);
	}
}
