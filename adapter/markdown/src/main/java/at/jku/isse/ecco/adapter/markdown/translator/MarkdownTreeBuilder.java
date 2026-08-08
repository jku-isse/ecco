package at.jku.isse.ecco.adapter.markdown.translator;

import at.jku.isse.ecco.adapter.markdown.data.BlockQuoteArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.BulletListArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.CodeBlockArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.HtmlBlockArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.LineArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.ListItemArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.OrderedListArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.ParagraphArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.SectionArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.TableArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.TableRowArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.ThematicBreakArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Translates a {@code commonmark-java} AST (parsed with {@link org.commonmark.parser.IncludeSourceSpans#BLOCKS})
 * into ECCO's {@code Node} tree, adding one grouping concept CommonMark's own AST doesn't have -
 * heading-based sections (see {@link #translate}) - and otherwise mapping each block type to its own
 * small {@code ArtifactData} (see the {@code data} package).
 * <p>
 * Every leaf node's content comes from the original {@code sourceLines} via each block's own {@link
 * org.commonmark.node.Node#getSourceSpans()} - never from re-rendering the parsed AST - so the writer
 * (a generic pre-order walk over whatever ends up here) reconstructs the original file byte-exact
 * regardless of any formatting the parser itself doesn't preserve. Lines no block claims (blank
 * separator lines) are re-attached as plain {@code LineArtifactData} wherever they fall between two
 * siblings, at whichever nesting depth that gap actually occurs at - see {@link #fillGap}.
 */
public final class MarkdownTreeBuilder {

	private final EntityFactory entityFactory;
	private final List<String> sourceLines;

	public MarkdownTreeBuilder(EntityFactory entityFactory, List<String> sourceLines) {
		this.entityFactory = entityFactory;
		this.sourceLines = sourceLines;
	}

	/**
	 * Translates {@code document}'s children into {@code pluginNode}'s children. Non-heading blocks
	 * become children of whichever section is currently innermost (or of {@code pluginNode} itself,
	 * before any heading has appeared); a heading closes every open section at its level or shallower
	 * and opens a new one.
	 */
	public void translate(Document document, Node.Op pluginNode) {
		Deque<SectionFrame> sectionStack = new ArrayDeque<>();
		sectionStack.push(new SectionFrame(Integer.MIN_VALUE, pluginNode, -1));

		for (org.commonmark.node.Node child = document.getFirstChild(); child != null; child = child.getNext()) {
			if (child instanceof Heading heading) {
				while (sectionStack.peek().level >= heading.getLevel()) {
					this.closeSection(sectionStack);
				}
				SectionFrame parent = sectionStack.peek();
				int headingLine = firstLine(heading);
				this.fillGap(parent, headingLine);
				String headingText = this.sourceLines.get(headingLine);
				Node.Op sectionNode = this.createOrderedChild(parent.node, new SectionArtifactData(heading.getLevel(), headingText));
				// the heading's own line(s) must also exist as a LineArtifactData child, not just as
				// SectionArtifactData's headingLine field - the writer is generic and only ever reads
				// LineArtifactData children, never block-specific fields (see MarkdownFileWriter).
				for (SourceSpan span : heading.getSourceSpans()) {
					this.addLineChild(sectionNode, span.getLineIndex());
				}
				parent.lastClaimedLine = lastLine(heading);
				sectionStack.push(new SectionFrame(heading.getLevel(), sectionNode, lastLine(heading)));
			} else {
				SectionFrame parent = sectionStack.peek();
				this.fillGap(parent, firstLine(child));
				this.translateBlock(child, parent.node);
				parent.lastClaimedLine = lastLine(child);
			}
		}

		while (sectionStack.size() > 1) {
			this.closeSection(sectionStack);
		}
		// trailing blank lines at the very end of the file, not bounded by any next sibling
		this.fillGap(sectionStack.peek(), this.sourceLines.size());
	}

	private void closeSection(Deque<SectionFrame> sectionStack) {
		SectionFrame closed = sectionStack.pop();
		// propagate how far the closed section actually consumed, so gap-filling under its parent
		// (for whatever sibling comes next) starts from the right position, not from before the
		// closed section's own content
		sectionStack.peek().lastClaimedLine = closed.lastClaimedLine;
	}

	/**
	 * Translates one block into a new child of {@code parentEccoNode} - the single dispatch point every
	 * block type (top-level or nested) goes through. Container types recurse via {@link
	 * #translateChildren}; leaf types wrap their own source lines directly.
	 */
	private void translateBlock(org.commonmark.node.Node block, Node.Op parentEccoNode) {
		if (block instanceof Paragraph) {
			this.addLeaf(parentEccoNode, new ParagraphArtifactData(), block);
		} else if (block instanceof FencedCodeBlock fencedCodeBlock) {
			this.addLeaf(parentEccoNode, new CodeBlockArtifactData(true, fencedCodeBlock.getInfo()), block);
		} else if (block instanceof IndentedCodeBlock) {
			this.addLeaf(parentEccoNode, new CodeBlockArtifactData(false, null), block);
		} else if (block instanceof ThematicBreak thematicBreak) {
			this.addLeaf(parentEccoNode, new ThematicBreakArtifactData(thematicBreak.getLiteral()), block);
		} else if (block instanceof HtmlBlock htmlBlock) {
			this.addLeaf(parentEccoNode, new HtmlBlockArtifactData(htmlBlock.getLiteral()), block);
		} else if (block instanceof BlockQuote) {
			Node.Op node = this.createOrderedChild(parentEccoNode, new BlockQuoteArtifactData());
			this.translateChildren(block, node);
		} else if (block instanceof BulletList bulletList) {
			Node.Op node = this.createOrderedChild(parentEccoNode, new BulletListArtifactData(bulletList.getMarker()));
			this.translateChildren(block, node);
		} else if (block instanceof OrderedList orderedList) {
			Node.Op node = this.createOrderedChild(parentEccoNode, new OrderedListArtifactData(orderedList.getMarkerStartNumber(), orderedList.getMarkerDelimiter()));
			this.translateChildren(block, node);
		} else if (block instanceof ListItem) {
			Node.Op node = this.createOrderedChild(parentEccoNode, new ListItemArtifactData());
			this.translateChildren(block, node);
		} else if (block instanceof TableBlock) {
			Node.Op node = this.createOrderedChild(parentEccoNode, new TableArtifactData());
			this.translateChildren(block, node);
		} else if (block instanceof TableRow tableRow) {
			boolean header = tableRow.getFirstChild() instanceof TableCell cell && cell.isHeader();
			this.addLeaf(parentEccoNode, new TableRowArtifactData(header), block);
		} else if (isTransparentTableGroup(block)) {
			// TableHead/TableBody: CommonMark's own grouping around table rows - not kept as separate
			// ECCO containers, since TableRowArtifactData's own header flag already carries that
			// distinction; their TableRow children are flattened straight into the TableArtifactData
			// parent, preserving document order (TableHead always precedes TableBody).
			this.translateChildren(block, parentEccoNode);
		} else {
			// Any other CommonMark node reachable here (only possible with an extension beyond
			// core + gfm-tables) falls back to a generic leaf rather than silently dropping content.
			this.addLeaf(parentEccoNode, new ParagraphArtifactData(), block);
		}
	}

	/** Translates every direct child of {@code commonmarkParent} into a child of {@code eccoParent}, filling any gaps between them. Reused for Document's own children (via {@link #translate}) and for every container type's children uniformly - CommonMark's sibling-walk API ({@code getFirstChild()}/{@code getNext()}) is the same for all of them. */
	private void translateChildren(org.commonmark.node.Node commonmarkParent, Node.Op eccoParent) {
		// seeded to just before this container's own first line, not -1/"start of file" - otherwise
		// gap-filling for this container's first child would reach back and re-claim lines that
		// belong to an earlier sibling *outside* this container entirely (e.g. a preceding list item).
		List<SourceSpan> ownSpans = commonmarkParent.getSourceSpans();
		int initialLastClaimedLine = ownSpans.isEmpty() ? -1 : ownSpans.get(0).getLineIndex() - 1;
		SectionFrame frame = new SectionFrame(Integer.MIN_VALUE, eccoParent, initialLastClaimedLine);

		for (org.commonmark.node.Node child = commonmarkParent.getFirstChild(); child != null; child = child.getNext()) {
			this.fillGap(frame, firstLine(child));
			this.translateBlock(child, eccoParent);
			frame.lastClaimedLine = lastLine(child);
		}
	}

	private void addLeaf(Node.Op parentEccoNode, ArtifactData data, org.commonmark.node.Node block) {
		Node.Op node = this.createOrderedChild(parentEccoNode, data);
		for (SourceSpan span : block.getSourceSpans()) {
			this.addLineChild(node, span.getLineIndex());
		}
	}

	private Node.Op createOrderedChild(Node.Op parent, ArtifactData data) {
		Node.Op node = this.entityFactory.createOrderedNode(data);
		parent.addChild(node);
		return node;
	}

	private void addLineChild(Node.Op parent, int lineIndex) {
		Node.Op lineNode = this.entityFactory.createNode(new LineArtifactData(this.sourceLines.get(lineIndex)));
		parent.addChild(lineNode);
	}

	/** Attaches every source line strictly between {@code frame}'s last claimed line and {@code nextClaimedLine} (exclusive) as a plain line child of {@code frame}'s node - the blank separator lines no block span ever claims. */
	private void fillGap(SectionFrame frame, int nextClaimedLine) {
		for (int line = frame.lastClaimedLine + 1; line < nextClaimedLine; line++) {
			this.addLineChild(frame.node, line);
		}
		frame.lastClaimedLine = Math.max(frame.lastClaimedLine, nextClaimedLine - 1);
	}

	private static boolean isTransparentTableGroup(org.commonmark.node.Node block) {
		String simpleName = block.getClass().getSimpleName();
		return "TableHead".equals(simpleName) || "TableBody".equals(simpleName);
	}

	private static int firstLine(org.commonmark.node.Node node) {
		return node.getSourceSpans().get(0).getLineIndex();
	}

	private static int lastLine(org.commonmark.node.Node node) {
		List<SourceSpan> spans = node.getSourceSpans();
		return spans.get(spans.size() - 1).getLineIndex();
	}

	/** One open section (or, with {@code level == Integer.MIN_VALUE}, a non-heading container's own children) being built, tracking how far its content has been filled in so far. */
	private static final class SectionFrame {
		final int level;
		final Node.Op node;
		int lastClaimedLine;

		/**
		 * @param lastClaimedLine where this frame's gap-tracking starts from - always the line right
		 *                        before this frame's own content can legitimately begin (the heading's
		 *                        own line for a section, or the container's own first line for a nested
		 *                        block), never a blanket -1/"start of file". Seeding it any earlier would
		 *                        let this frame's first gap-fill reach backward and re-claim lines that
		 *                        belong to an earlier sibling entirely outside this frame.
		 */
		SectionFrame(int level, Node.Op node, int lastClaimedLine) {
			this.level = level;
			this.node = node;
			this.lastClaimedLine = lastClaimedLine;
		}
	}

}
