package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.adapter.SourcePosition;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MarkdownPositionsTest {

	@Test
	public void sectionsByHeadingWithTheirLines(@TempDir Path base) throws Exception {
		Files.writeString(base.resolve("README.md"), """
				intro

				# Glean

				text

				## Installation

				- one
				- two

				### Docker ###

				```
				docker run
				```
				""");
		MarkdownReader reader = new MarkdownReader(new SerEntityFactory());
		Node file = reader.read(base, new Path[]{Path.of("README.md")}).iterator().next();

		assertEquals(List.of(
						new SourcePosition("section", "Glean", 3, 16),
						new SourcePosition("section", "Glean > Installation", 7, 16),
						new SourcePosition("section", "Glean > Installation > Docker", 12, 16)),
				reader.positions(file).stream().sorted(Comparator.comparingInt(SourcePosition::startLine)).toList());
	}
}
