package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.markdown.translator.MarkdownTreeBuilder;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Document;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reads a Markdown (CommonMark + GFM tables) file into ECCO's tree - see {@link MarkdownTreeBuilder}
 * for the actual AST-to-tree translation this delegates to. {@code getPrioritizedPatterns()} claims
 * {@code .md}/{@code .markdown} at priority {@code 2}, above {@code TextReader}'s existing (line-only)
 * priority-{@code 1} claim on {@code **.md}, so this wins when a fresh repository's {@code .adapters}
 * file is generated.
 */
public class MarkdownReader implements ArtifactReader<Path, Set<Node.Op>> {

	private final EntityFactory entityFactory;
	private final Parser parser;

	@Inject
	public MarkdownReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
		this.parser = Parser.builder()
				.extensions(List.of(TablesExtension.create()))
				.includeSourceSpans(IncludeSourceSpans.BLOCKS)
				.build();
	}

	@Override
	public String getPluginId() {
		return MarkdownPlugin.class.getName();
	}

	private static final Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(2, new String[]{"**.md", "**.markdown"});
	}

	@Override
	public Map<Integer, String[]> getPrioritizedPatterns() {
		return Collections.unmodifiableMap(prioritizedPatterns);
	}

	@Override
	public Set<Node.Op> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		Set<Node.Op> nodes = new HashSet<>();
		for (Path path : input) {
			Path resolvedPath = base.resolve(path);
			Node.Op pluginNode = this.entityFactory.createOrderedNode(new PluginArtifactData(this.getPluginId(), path));
			nodes.add(pluginNode);

			try {
				List<String> sourceLines = Files.readAllLines(resolvedPath, StandardCharsets.UTF_8);
				String content = String.join("\n", sourceLines);
				Document document = (Document) this.parser.parse(content);
				new MarkdownTreeBuilder(this.entityFactory, sourceLines).translate(document, pluginNode);
			} catch (IOException e) {
				throw new EccoException("Could not read file: " + resolvedPath, e);
			}
		}
		return nodes;
	}


	private final Collection<ReadListener> listeners = new ArrayList<>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
