package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.markdown.data.LineArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MarkdownFileWriterTest {

	private final MarkdownReader reader = new MarkdownReader(new SerEntityFactory());
	private final MarkdownFileWriter writer = new MarkdownFileWriter();

	private void assertRoundTrips(String content) throws IOException {
		Path baseDir = Files.createTempDirectory("markdown-writer-roundtrip");
		Files.writeString(baseDir.resolve("a.md"), content);
		Set<Node> read = Set.copyOf(this.reader.read(baseDir, new Path[]{Path.of("a.md")}));

		Path outputDir = Files.createTempDirectory("markdown-writer-roundtrip-out");
		Path[] written = this.writer.write(outputDir, read);

		assertEquals(1, written.length);
		assertEquals(content, Files.readString(written[0]));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"Some paragraph\ntext that spans two lines.\n",
			"""
					# Title

					intro text

					## Sub

					sub text

					### SubSub

					subsub text
					""",
			"""
					# Real heading

					```bash
					#!/bin/bash
					# not a heading
					echo hi
					```
					""",
			"""
					- outer 1
					  1. inner a
					  2. inner b
					- outer 2
					""",
			"""
					- outer 1
					- outer 2

					  ```
					  code in list item
					  ```
					""",
			"""
					> - quoted item one
					> - quoted item two
					""",
			"""
					| a | b |
					|---|---|
					| 1 | 2 |
					| 3 | 4 |
					""",
			// blank-line preservation between and around top-level blocks, including a leading blank line
			"""

					# Title

					para one


					para two
					---


					# Two
					""",
	})
	public void writeRoundTripsReadContentBackToDiskByteExact(String content) throws IOException {
		this.assertRoundTrips(content);
	}

	@Test
	public void writeThrowsForANodeWithoutPluginArtifactData() {
		Node.Op bareNode = new SerEntityFactory().createNode(new LineArtifactData("not a file"));
		assertThrows(EccoException.class, () -> this.writer.write(Set.of(bareNode)));
	}

	@Test
	public void getPluginIdIsTheMarkdownPluginClassName() {
		assertEquals(MarkdownPlugin.class.getName(), this.writer.getPluginId());
	}

}
