package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginArtifactData implements ArtifactData {

	private static final long serialVersionUID = 113880049592859344L;

	private String pluginId;
	private transient Path path;
	private String pathString;

	// the text format of the file as last read by a line-based adapter (see TextFileFormat), so that
	// checkout reproduces it byte for byte; null for non-text adapters and for files committed before
	// this was recorded. Deliberately not part of equals()/hashCode(): the file's identity is its path.
	private String charset;
	private String lineSeparator;
	private Boolean endsWithNewline;
	// whether the file's final newline is a FinalNewlineArtifactData child of its node, traced like a
	// line, instead of endsWithNewline; false for files stored before (a field their data lacks).
	// Primitive: as a Boolean it shared one object with endsWithNewline when serialized, and after
	// adoption no longer did - every file's data was written again on every commit, unchanged.
	private boolean finalNewlineTraced;
	// whether the file's charset and line separator are a TextFormatArtifactData child of its node,
	// traced like a line, instead of charset/lineSeparator; false for files stored before. Primitive,
	// as finalNewlineTraced.
	private boolean formatTraced;

	protected PluginArtifactData() {
		this.pluginId = null;
		this.path = null;
		this.pathString = null;
	}

	public PluginArtifactData(String pluginId, Path path) {
		this.pluginId = pluginId;
		this.path = path;
		this.pathString = path.toString();
	}

	public String getPluginId() {
		return this.pluginId;
	}

	public Path getFileName() {
		return this.getPath().getFileName();
	}

	public Path getPath() {
		if (this.path == null && this.pathString != null) {
			this.path = Paths.get(this.pathString);
		}
		return this.path;
	}

	public void setTextFormat(String charset, String lineSeparator, boolean endsWithNewline) {
		this.charset = charset;
		this.lineSeparator = lineSeparator;
		this.endsWithNewline = endsWithNewline;
	}

	/** Records that the file's final newline is a child of its node (FinalNewlineArtifactData). */
	public void setFinalNewlineTraced() {
		this.finalNewlineTraced = true;
	}

	/** Records that the file's charset and line separator are a child of its node (TextFormatArtifactData). */
	public void setFormatTraced() {
		this.formatTraced = true;
	}

	/** Whether the file's charset and line separator are a child of its node; else getCharset()/getLineSeparator() say. */
	public boolean isFormatTraced() {
		return this.formatTraced;
	}

	/** Whether the file's final newline is a child of its node; else endsWithNewline() says. */
	public boolean isFinalNewlineTraced() {
		return this.finalNewlineTraced;
	}

	/**
	 * The newest commit's text format wins (see ArtifactData.adoptMetadataFrom()) - also how files
	 * committed before formats were recorded get one. Whether the final newline and the format are
	 * traced does not change: a file stored before has no such child in the variants committed before.
	 */
	@Override
	public void adoptMetadataFrom(ArtifactData newer) {
		if (newer instanceof PluginArtifactData newerData && newerData.hasTextFormat())
			this.setTextFormat(newerData.charset, newerData.lineSeparator, newerData.endsWithNewline);
	}

	public boolean hasTextFormat() {
		return this.charset != null && this.lineSeparator != null && this.endsWithNewline != null;
	}

	public String getCharset() {
		return this.charset;
	}

	public String getLineSeparator() {
		return this.lineSeparator;
	}

	public boolean endsWithNewline() {
		return Boolean.TRUE.equals(this.endsWithNewline);
	}

	@Override
	public String toString() {
		return this.getPath().toString() + " [" + this.pluginId + "]";
	}

	@Override
	public int hashCode() {
		return this.getPath().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PluginArtifactData that = (PluginArtifactData) o;

		return this.getPath().equals(that.getPath());

	}


	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		this.path = Paths.get(this.pathString);
	}

}
