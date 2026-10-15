package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes line-based text files so that a commit/checkout round trip reproduces them byte
 * for byte. Line adapters used to read with the default charset and write every line followed by
 * the platform line separator, so checking a file out normalized CRLF to LF, appended a missing
 * final newline, and - worst - replaced every byte that isn't valid UTF-8 (e.g. a Latin-1 'é') with
 * U+FFFD at commit time, losing it for good.
 * <p>
 * The file's format (charset, line separator, final newline) is recorded on its
 * {@link PluginArtifactData}. Lines are split exactly like {@link java.io.BufferedReader#readLine()}
 * (on \n, \r\n and \r), so the line artifacts of existing repositories stay the same. Files whose
 * format was never recorded (committed before this existed) are written exactly as before.
 * A file mixing line separators keeps the others in its format ({@link TextFormatArtifactData#getOtherSeparators()};
 * it got the first one throughout); a checkout combining variants in different formats writes the
 * newest commit's main separator throughout. Each variant's final
 * newline ({@link FinalNewlineArtifactData}) and its charset and line separator
 * ({@link TextFormatArtifactData}) are children of the file's node, traced like lines - recorded on
 * the plugin artifact, which the variants of a file share, the newest commit's were written into
 * every variant. Files stored before keep that: the plugin data says whether they are traced.
 */
public final class TextFileFormat {

	private TextFileFormat() {
	}

	/** [otherSeparators]: the separator after each line (by index) that ends otherwise than with [lineSeparator]. */
	public record Decoded(List<String> lines, Charset charset, String lineSeparator, boolean endsWithNewline, Map<Integer, String> otherSeparators) {

		/**
		 * Records this file's format on its plugin artifact data, for {@link #newLineWriter}.
		 */
		public void recordOn(PluginArtifactData data) {
			data.setTextFormat(this.charset.name(), this.lineSeparator, this.endsWithNewline);
			data.setFinalNewlineTraced();
			data.setFormatTraced();
		}

		/**
		 * Adds the file's final newline, if it has one (see {@link FinalNewlineArtifactData}), and its
		 * charset and line separator ({@link TextFormatArtifactData}, unless it is empty) as the last
		 * children of its node - after its lines.
		 */
		public void addMarkers(Node.Op fileNode, EntityFactory entityFactory) {
			if (this.endsWithNewline)
				fileNode.addChild(entityFactory.createNode(entityFactory.createArtifact(new FinalNewlineArtifactData())));
			// an empty file is written the same in every format
			if (!this.lines.isEmpty())
				fileNode.addChild(entityFactory.createNode(entityFactory.createArtifact(new TextFormatArtifactData(this.charset.name(), this.lineSeparator, this.otherSeparators))));
		}
	}

	/** Whether [node] is a file's final newline, which a writer does not write as a line. */
	public static boolean isFinalNewline(Node node) {
		return node.getArtifact() != null && node.getArtifact().getData() instanceof FinalNewlineArtifactData;
	}

	/** Whether [node] is a file's final newline or format - written as neither a line nor anything else. */
	public static boolean isMarker(Node node) {
		return isFinalNewline(node) || node.getArtifact() != null && node.getArtifact().getData() instanceof TextFormatArtifactData;
	}

	/**
	 * Reads a text file: strict UTF-8, or - if the bytes aren't valid UTF-8 - ISO-8859-1, which maps
	 * every byte to exactly one character and back, so nothing is lost.
	 */
	public static Decoded read(Path file) throws IOException {
		byte[] bytes = Files.readAllBytes(file);
		Charset charset = StandardCharsets.UTF_8;
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			charset = StandardCharsets.ISO_8859_1;
			text = new String(bytes, charset);
		}

		List<String> lines = new ArrayList<>();
		String lineSeparator = null;
		Map<Integer, String> otherSeparators = new TreeMap<>();
		int start = 0;
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '\n' || c == '\r') {
				lines.add(text.substring(start, i));
				int terminatorLength = (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') ? 2 : 1;
				String separator = text.substring(i, i + terminatorLength);
				if (lineSeparator == null)
					lineSeparator = separator;
				else if (!separator.equals(lineSeparator))
					otherSeparators.put(lines.size() - 1, separator);
				i += terminatorLength;
				start = i;
			} else {
				i++;
			}
		}
		boolean endsWithNewline = start == text.length() && !text.isEmpty();
		if (start < text.length())
			lines.add(text.substring(start));
		return new Decoded(lines, charset, lineSeparator == null ? "\n" : lineSeparator, endsWithNewline, otherSeparators);
	}

	/**
	 * Opens a writer for {@code file} that reproduces the format recorded on {@code data}, or - for
	 * data without a recorded format - writes the way line adapters always did: UTF-8, every line
	 * followed by the platform line separator.
	 */
	public static LineWriter newLineWriter(Path file, PluginArtifactData data, Node fileNode) throws IOException {
		if (!data.hasTextFormat())
			return new LineWriter(Files.newBufferedWriter(file), System.lineSeparator(), true, Map.of());
		TextFormatArtifactData format = format(data, fileNode);
		return new LineWriter(Files.newBufferedWriter(file, Charset.forName(format.getCharset())), format.getLineSeparator(), endsWithNewline(data, fileNode),
				format.getOtherSeparators());
	}

	/**
	 * The charset and line separator of the file [fileNode] stands for in this variant: its format
	 * child; the recorded (newest) one for a file stored before formats were traced, without a format
	 * child, or with several - a checkout combining variants in different formats.
	 */
	public static TextFormatArtifactData format(PluginArtifactData data, Node fileNode) {
		TextFormatArtifactData newest = new TextFormatArtifactData(data.getCharset(), data.getLineSeparator());
		if (!data.isFormatTraced())
			return newest;
		TextFormatArtifactData found = null;
		for (Node child : fileNode.getChildren()) {
			if (child.getArtifact() != null && child.getArtifact().getData() instanceof TextFormatArtifactData format) {
				if (found != null && !found.equals(format))
					return newest;
				found = format;
			}
		}
		return found == null ? newest : found;
	}

	/** Whether the file [fileNode] stands for ends with a newline in this variant. */
	public static boolean endsWithNewline(PluginArtifactData data, Node fileNode) {
		if (!data.isFinalNewlineTraced())
			return data.endsWithNewline();
		for (Node child : fileNode.getChildren())
			if (isFinalNewline(child))
				return true;
		return false;
	}

	public static final class LineWriter implements Closeable {
		private final BufferedWriter writer;
		private final String lineSeparator;
		private final boolean endsWithNewline;
		private final Map<Integer, String> otherSeparators;
		private int written;

		private LineWriter(BufferedWriter writer, String lineSeparator, boolean endsWithNewline, Map<Integer, String> otherSeparators) {
			this.writer = writer;
			this.lineSeparator = lineSeparator;
			this.endsWithNewline = endsWithNewline;
			this.otherSeparators = otherSeparators;
		}

		/** The separator after the line with index [line]. */
		private String separatorAfter(int line) {
			return this.otherSeparators.getOrDefault(line, this.lineSeparator);
		}

		public void writeLine(String line) throws IOException {
			if (this.written > 0)
				this.writer.write(this.separatorAfter(this.written - 1));
			this.writer.write(line);
			this.written++;
		}

		@Override
		public void close() throws IOException {
			try {
				if (this.written > 0 && this.endsWithNewline)
					this.writer.write(this.separatorAfter(this.written - 1));
			} finally {
				this.writer.close();
			}
		}
	}
}
