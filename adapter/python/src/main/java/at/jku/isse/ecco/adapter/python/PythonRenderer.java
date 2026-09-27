package at.jku.isse.ecco.adapter.python;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.JsonArrayArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.JsonFieldArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.JsonObjectArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.value.JsonNullValueArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.value.JsonStringArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.value.JsonValueArtifactData;
import at.jku.isse.ecco.adapter.python.data.jupyter.JupyterCellArtifactData;
import at.jku.isse.ecco.adapter.python.data.jupyter.JupyterLineArtifactData;
import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Python adapter's files as the association preview ({@link PythonViewer}) shows them.
 * <ul>
 * <li>Python: its tree holds libcst nodes, which only Python can turn into code - the writer
 * script's render mode produces the code and reports where each node's code is, so this needs the
 * same {@code python} (with libcst and py4j) as committing and checking out.</li>
 * <li>JSON: formatted like the writer's {@code json.dumps(indent=4)}.</li>
 * <li>Jupyter notebooks: the cells, each under a {@code # %%} line (the percent format of
 * Jupytext and many editors) - the notebook's JSON around them is left out.</li>
 * </ul>
 */
public final class PythonRenderer {

	private PythonRenderer() {
	}

	public static RenderedSource render(Node fileNode) throws IOException {
		Path path = ((PluginArtifactData) fileNode.getArtifact().getData()).getPath();
		String name = path.getFileName().toString();
		if (name.endsWith(".json")) {
			Builder builder = new Builder();
			for (Node child : fileNode.getChildren())
				renderJson(child, builder, 0);
			return builder.build();
		}
		if (name.endsWith(".ipynb"))
			return renderNotebook(fileNode, path);
		if (fileNode.getChildren().size() != 1)
			throw new IOException("Expected one module node below " + path + ", found " + fileNode.getChildren().size() + ".");
		return withParser(parser -> parser.render(path, fileNode.getChildren().get(0)));
	}

	private interface ParserUse<T> {
		T apply(PythonParser.Writer parser) throws IOException;
	}

	private static <T> T withParser(ParserUse<T> use) throws IOException {
		PythonParser.Writer parser = PythonParserFactory.getWriteParser();
		if (parser == null)
			throw new IOException("No Python writer available.");
		parser.init();
		try {
			return use.apply(parser);
		} finally {
			parser.shutdown();
		}
	}

	// ---- Jupyter

	private static RenderedSource renderNotebook(Node fileNode, Path path) throws IOException {
		List<Node> cells = new ArrayList<>();
		collectCells(fileNode, cells);
		return withParser(parser -> {
			Builder builder = new Builder();
			for (Node cell : cells) {
				JupyterCellArtifactData data = (JupyterCellArtifactData) cell.getArtifact().getData();
				int start = builder.length();
				if (builder.length() > 0)
					builder.append("\n");
				builder.append("markdown".equals(data.getCellType()) ? "# %% [markdown]\n" : "# %%\n");
				if ("code".equals(data.getCellType()) && "code".equals(data.getParseType()) && cell.getChildren().size() == 1) {
					builder.append(parser.render(path, cell.getChildren().get(0)));
				} else {
					for (Node line : cell.getChildren()) {
						if (line.getArtifact().getData() instanceof JupyterLineArtifactData lineData) {
							int lineStart = builder.length();
							String text = lineData.getLine();
							builder.append(text.endsWith("\n") ? text : text + "\n");
							builder.span(line, lineStart);
						}
					}
				}
				builder.span(cell, start);
			}
			return builder.build();
		});
	}

	private static void collectCells(Node node, List<Node> cells) {
		if (node.getArtifact() != null && node.getArtifact().getData() instanceof JupyterCellArtifactData) {
			cells.add(node);
			return;
		}
		for (Node child : node.getChildren())
			collectCells(child, cells);
	}

	// ---- JSON, laid out like json.dumps(value, indent=4)

	private static void renderJson(Node node, Builder builder, int depth) {
		int start = builder.length();
		ArtifactData data = node.getArtifact().getData();
		if (data instanceof JsonObjectArtifactData || data instanceof JsonArrayArtifactData) {
			boolean object = data instanceof JsonObjectArtifactData;
			List<? extends Node> children = node.getChildren();
			if (children.isEmpty()) {
				builder.append(object ? "{}" : "[]");
			} else {
				builder.append(object ? "{\n" : "[\n");
				for (int i = 0; i < children.size(); i++) {
					builder.append("    ".repeat(depth + 1));
					renderJson(children.get(i), builder, depth + 1);
					builder.append(i < children.size() - 1 ? ",\n" : "\n");
				}
				builder.append("    ".repeat(depth)).append(object ? "}" : "]");
			}
		} else if (data instanceof JsonFieldArtifactData field) {
			builder.append(quote(field.getFieldName())).append(": ");
			if (node.getChildren().isEmpty())
				builder.append("null");
			else
				renderJson(node.getChildren().get(0), builder, depth);
		} else if (data instanceof JsonStringArtifactData string) {
			builder.append(quote(string.getValue()));
		} else if (data instanceof JsonNullValueArtifactData) {
			builder.append("null");
		} else if (data instanceof JsonValueArtifactData<?> value) {
			builder.append(String.valueOf(value.getValue()));
		} else {
			builder.append(String.valueOf(data));
		}
		builder.span(node, start);
	}

	/** A JSON string literal as json.dumps writes it (ensure_ascii). */
	private static String quote(String value) {
		StringBuilder quoted = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> quoted.append("\\\"");
				case '\\' -> quoted.append("\\\\");
				case '\n' -> quoted.append("\\n");
				case '\r' -> quoted.append("\\r");
				case '\t' -> quoted.append("\\t");
				case '\b' -> quoted.append("\\b");
				case '\f' -> quoted.append("\\f");
				default -> {
					if (c < 0x20 || c > 0x7e)
						quoted.append(String.format("\\u%04x", (int) c));
					else
						quoted.append(c);
				}
			}
		}
		return quoted.append('"').toString();
	}

	/** Collects text and spans; a nested rendering is appended with its spans shifted. */
	private static final class Builder {
		private final StringBuilder text = new StringBuilder();
		private final List<RenderedSource.Span> spans = new ArrayList<>();

		int length() {
			return this.text.length();
		}

		Builder append(String s) {
			this.text.append(s);
			return this;
		}

		void append(RenderedSource source) {
			int offset = this.text.length();
			this.text.append(source.getText());
			if (!source.getText().endsWith("\n"))
				this.text.append('\n');
			for (RenderedSource.Span span : source.getSpans())
				this.spans.add(new RenderedSource.Span(span.node(), span.start() + offset, span.end() + offset));
		}

		void span(Node node, int start) {
			this.spans.add(new RenderedSource.Span(node, start, this.text.length()));
		}

		RenderedSource build() {
			return new RenderedSource(this.text.toString(), this.spans);
		}
	}
}
