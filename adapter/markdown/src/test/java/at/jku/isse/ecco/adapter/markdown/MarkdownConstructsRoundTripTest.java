package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Read and write give back every CommonMark/GFM construct byte for byte, in LF, CRLF and CR files,
 * with and without a final newline - and the reader's and writer's other entry points.
 */
public class MarkdownConstructsRoundTripTest {

	@TempDir
	Path tmp;

	private byte[] roundTrip(byte[] source) throws IOException {
		Path in = Files.createTempDirectory(this.tmp, "in");
		Files.write(in.resolve("a.md"), source);
		Set<Node.Op> read = new MarkdownReader(new SerEntityFactory()).read(in, new Path[]{Path.of("a.md")});
		Path out = Files.createTempDirectory(this.tmp, "out");
		Path[] written = new MarkdownFileWriter().write(out, Set.copyOf(read));
		assertEquals(1, written.length);
		return Files.readAllBytes(written[0]);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// code: fenced with ``` and ~~~, longer fences, info strings with attributes, indented, unclosed
			"```java\nint x;\n```\n",
			"~~~kotlin\nval x = 1\n~~~\n",
			"````md\n```\nnested fence\n```\n````\n",
			"``` java {.numberLines}\nx\n```\n",
			"~~~\n```\nnot a close\n~~~\n",
			"    indented\n\n\n    after two blanks\n",
			"\tindented with a tab\n",
			"```\nunclosed\n\nto the end\n",
			"  ```\n  indented fence\n  ```\n",
			// lists: nested, tight, loose, ordered from other numbers, with ")", mixed markers
			"- a\n  - b\n    - c\n- d\n",
			"- a\n\n- b\n\n- c\n",
			"3. three\n4. four\n",
			"1) a\n2) b\n",
			"007. leading zeros\n",
			"- a\n* b\n+ c\n",
			"1. a\n\n   para in item\n\n   ```\n   code in item\n   ```\n2. b\n",
			"- [ ] task\n- [x] done\n",
			"-\n  empty first line\n",
			// headings: ATX with closing hashes and trailing spaces, setext both levels, multi-line setext
			"# H1 #\n## H2 ##   \n###### H6\n####### not a heading\n",
			"Title\n=====\n\nSub\n---\n",
			"Two\nline title\n===\n",
			"#\n",
			// block quotes: lazy continuation, nested, containing a heading and code
			"> quoted\nlazy continuation\n",
			"> outer\n>> inner\n> back\n",
			"> # heading in quote\n> ```\n> code\n> ```\n",
			// HTML blocks
			"<div>\n*not markdown*\n</div>\n",
			"<!-- comment\nspanning -->\n",
			"<details>\n<summary>s</summary>\n\nbody\n\n</details>\n",
			"<?php echo 1; ?>\n",
			// tables with alignment, escaped pipes, without leading pipes, header only
			"| L | C | R |\n|:--|:-:|--:|\n| 1 | 2 | 3 |\n",
			"a | b\n--|--\n1 | 2\n",
			"| a |\n|---|\n",
			"| a |\n|---|\n|\n",
			"| a | b |\n|---|---|\n| only one |\n| 1 | 2 | 3 |\n",
			"| a |\n|---|\n| 1 |\n\n| b |\n|---|\n",
			"- | a |\n  |---|\n  | 1 |\n",
			"| x \\| y |\n|---|\n| z |\n",
			// thematic breaks
			"***\n---\n___\n- - -\n * * *\n",
			"para\n\n---\n",
			// hard line breaks, trailing whitespace, tabs
			"line with two spaces  \nnext\n",
			"backslash break\\\nnext\n",
			"trailing tab\t\n\t\n   \n",
			// front matter (no extension: a thematic break and a setext heading) and link reference definitions
			"---\ntitle: x\n---\n\n# Doc\n",
			"[ref]: http://example.org \"t\"\n[ref2]:\n  http://example.org\n\ntext [ref]\n",
			// no final newline, blank lines only, leading blank lines
			"no final newline",
			"# H\n\ntext",
			"\n\n\n",
			"\n\n# late heading\n",
			"",
	})
	public void everyConstructComesBackInEveryLineEnding(String content) throws IOException {
		for (String separator : new String[]{"\n", "\r\n", "\r"}) {
			byte[] source = content.replace("\n", separator).getBytes(StandardCharsets.UTF_8);
			assertArrayEquals(source, this.roundTrip(source), content.replace("\n", "\\n") + " with " + separator.replace("\r", "\\r").replace("\n", "\\n"));
		}
	}

	@Test
	public void nonAsciiAndLatin1ComeBack() throws IOException {
		byte[] utf8 = "# Größe €\n\n- café\n".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(utf8, this.roundTrip(utf8));
		byte[] latin1 = "# Größe\n\n- café\n".getBytes(StandardCharsets.ISO_8859_1);
		assertArrayEquals(latin1, this.roundTrip(latin1));
		byte[] bom = "\uFEFF# BOM\n".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(bom, this.roundTrip(bom));
	}

	/** A file mixing line endings keeps each (gap #53): it got its first one throughout. */
	@Test
	public void aFileMixingLineEndingsKeepsThem() throws IOException {
		byte[] mixed = "# a\r\nb\nc\r\n".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(mixed, this.roundTrip(mixed));
	}

	/** The overloads without a base directory resolve against the working directory. */
	@Test
	public void readAndWriteWithoutABaseDirectoryUseTheWorkingDirectory() throws IOException {
		Path relative = Path.of("build", "tmp", "markdown-relative-" + System.nanoTime(), "a.md");
		Files.createDirectories(relative.getParent());
		try {
			Files.writeString(relative, "# T\r\n\r\ntext\r\n");
			Set<Node.Op> read = new MarkdownReader(new SerEntityFactory()).read(new Path[]{relative});
			assertEquals(relative, ((PluginArtifactData) read.iterator().next().getArtifact().getData()).getPath());
			Files.delete(relative);
			Path[] written = new MarkdownFileWriter().write(Set.copyOf(read));
			assertArrayEquals(new Path[]{Path.of(".").resolve(relative)}, written);
			assertEquals("# T\r\n\r\ntext\r\n", Files.readString(relative));
		} finally {
			Files.deleteIfExists(relative);
			Files.deleteIfExists(relative.getParent());
		}
	}

	@Test
	public void aMissingFileIsAReadError() {
		EccoException e = assertThrows(EccoException.class,
				() -> new MarkdownReader(new SerEntityFactory()).read(this.tmp, new Path[]{Path.of("missing.md")}));
		assertEquals("Could not read file: " + this.tmp.resolve("missing.md"), e.getMessage());
	}

	@Test
	public void aFileThatCannotBeWrittenIsAWriteError() throws IOException {
		Path in = Files.createTempDirectory(this.tmp, "in");
		Files.writeString(in.resolve("a.md"), "text\n");
		Set<Node.Op> read = new MarkdownReader(new SerEntityFactory()).read(in, new Path[]{Path.of("a.md")});
		Path out = Files.createTempDirectory(this.tmp, "out");
		Files.createDirectories(out.resolve("a.md")); // a directory where the file goes
		EccoException e = assertThrows(EccoException.class, () -> new MarkdownFileWriter().write(out, Set.copyOf(read)));
		assertEquals("Could not write file: " + out.resolve("a.md"), e.getMessage());
	}

	/** Listeners are accepted and removed; neither the reader nor the writer calls them. */
	@Test
	public void listenersCanBeAddedAndRemoved() throws IOException {
		int[] calls = {0};
		ReadListener readListener = new ReadListener() {
			@Override
			public void fileReadEvent(Path file, ArtifactReader reader) {
				calls[0]++;
			}
		};
		WriteListener writeListener = new WriteListener() {
			@Override
			public void fileWriteEvent(Path file, ArtifactWriter writer) {
				calls[0]++;
			}
		};
		MarkdownReader reader = new MarkdownReader(new SerEntityFactory());
		MarkdownFileWriter writer = new MarkdownFileWriter();
		reader.addListener(readListener);
		writer.addListener(writeListener);
		Path in = Files.createTempDirectory(this.tmp, "in");
		Files.writeString(in.resolve("a.md"), "text\n");
		Set<Node.Op> read = reader.read(in, new Path[]{Path.of("a.md")});
		writer.write(Files.createTempDirectory(this.tmp, "out"), Set.copyOf(read));
		reader.removeListener(readListener);
		writer.removeListener(writeListener);
		assertEquals(0, calls[0]);
	}
}
