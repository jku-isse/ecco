package at.jku.isse.ecco.adapter.dispatch;

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
 * Limitations: a file mixing line separators gets the first one throughout, and since variants of a
 * file share its plugin artifact, the recorded format is the one stored with that artifact.
 */
public final class TextFileFormat {

	private TextFileFormat() {
	}

	public record Decoded(List<String> lines, Charset charset, String lineSeparator, boolean endsWithNewline) {

		/**
		 * Records this file's format on its plugin artifact data, for {@link #newLineWriter}.
		 */
		public void recordOn(PluginArtifactData data) {
			data.setTextFormat(this.charset.name(), this.lineSeparator, this.endsWithNewline);
		}
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
		int start = 0;
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '\n' || c == '\r') {
				lines.add(text.substring(start, i));
				int terminatorLength = (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') ? 2 : 1;
				if (lineSeparator == null)
					lineSeparator = text.substring(i, i + terminatorLength);
				i += terminatorLength;
				start = i;
			} else {
				i++;
			}
		}
		boolean endsWithNewline = start == text.length() && !text.isEmpty();
		if (start < text.length())
			lines.add(text.substring(start));
		return new Decoded(lines, charset, lineSeparator == null ? "\n" : lineSeparator, endsWithNewline);
	}

	/**
	 * Opens a writer for {@code file} that reproduces the format recorded on {@code data}, or - for
	 * data without a recorded format - writes the way line adapters always did: UTF-8, every line
	 * followed by the platform line separator.
	 */
	public static LineWriter newLineWriter(Path file, PluginArtifactData data) throws IOException {
		if (!data.hasTextFormat())
			return new LineWriter(Files.newBufferedWriter(file), System.lineSeparator(), true);
		return new LineWriter(Files.newBufferedWriter(file, Charset.forName(data.getCharset())), data.getLineSeparator(), data.endsWithNewline());
	}

	public static final class LineWriter implements Closeable {
		private final BufferedWriter writer;
		private final String lineSeparator;
		private final boolean endsWithNewline;
		private boolean first = true;

		private LineWriter(BufferedWriter writer, String lineSeparator, boolean endsWithNewline) {
			this.writer = writer;
			this.lineSeparator = lineSeparator;
			this.endsWithNewline = endsWithNewline;
		}

		public void writeLine(String line) throws IOException {
			if (!this.first)
				this.writer.write(this.lineSeparator);
			this.writer.write(line);
			this.first = false;
		}

		@Override
		public void close() throws IOException {
			try {
				if (!this.first && this.endsWithNewline)
					this.writer.write(this.lineSeparator);
			} finally {
				this.writer.close();
			}
		}
	}
}
