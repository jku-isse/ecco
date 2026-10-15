package at.jku.cdl.ecco.adapter.java;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleDirective;
import com.github.javaparser.ast.modules.ModuleExportsDirective;
import com.github.javaparser.ast.modules.ModuleOpensDirective;
import com.github.javaparser.ast.modules.ModuleProvidesDirective;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;
import com.github.javaparser.ast.modules.ModuleUsesDirective;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

/**
 * JavaParser's printer, with the comments inside statements and declarations where they were: the
 * adapter keeps the text of a part with comments (JavaASTData#getTextWithComments and the like),
 * printed by this printer when read and parsed and printed by it again when written. JavaParser's
 * own printer drops some of these comments (in an empty argument list, before a lambda's expression
 * body) and puts a line break after every block comment, also inside a line ({@code case /* c *&#47; 1:}).
 */
final class JavaASTPrinter {

	private JavaASTPrinter() {
	}

	/** The comments before a node's own one (JavaASTData#getLeadingComments), kept on the node written. */
	static final DataKey<List<Comment>> LEADING_COMMENTS = new DataKey<>() {
	};

	/** {@code node} printed with its comments. */
	static String print(Node node) {
		return new DefaultPrettyPrinter(Visitor::new, new DefaultPrinterConfiguration()).print(node);
	}

	/**
	 * JavaParser gives a line comment behind code on its line to every node ending on that line
	 * ({@code g(xs.length // len} to both {@code g} and {@code xs.length}), so it was printed, and
	 * written, once for each. Each such comment is left only with the node JavaParser gave it last,
	 * the one right before it.
	 */
	static void keepCommentsOnce(Node root) {
		Map<Comment, List<Node>> holders = new IdentityHashMap<>();
		root.walk(node -> node.getComment().ifPresent(comment -> holders.computeIfAbsent(comment, c -> new ArrayList<>()).add(node)));
		holders.forEach((comment, nodes) -> {
			if (nodes.size() < 2)
				return;
			Node keeper = comment.getCommentedNode().orElse(nodes.get(nodes.size() - 1));
			for (Node node : nodes)
				node.setComment(null);
			keeper.setComment(comment);
		});
	}

	/**
	 * For text this printer printed, which puts every comment before the node it belongs to: a line
	 * comment the parser gave to code before it on its line ({@code int a = // one} to {@code int}
	 * and {@code a}, see keepCommentsOnce) belongs to the code after it, and is given to that. A
	 * comment between a binary operator and its right operand used to be written twice that way.
	 */
	static void giveCommentsToTheCodeAfter(Node root) {
		Map<Comment, List<Node>> holders = new IdentityHashMap<>();
		root.walk(node -> node.getComment().ifPresent(comment -> {
			if (comment instanceof LineComment && comment.getRange().isPresent() && node.getRange().isPresent()
					&& node.getRange().get().end.isBefore(comment.getRange().get().begin))
				holders.computeIfAbsent(comment, c -> new ArrayList<>()).add(node);
		}));
		holders.forEach((comment, nodes) -> {
			Node after = null;
			for (Node candidate : root.findAll(Node.class)) {
				if (!(candidate instanceof Comment) && candidate.getComment().isEmpty() && candidate.getRange().isPresent()
						&& candidate.getRange().get().begin.isAfter(comment.getRange().get().end)
						&& (after == null || candidate.getRange().get().begin.isBefore(after.getRange().get().begin))) {
					after = candidate;
				}
			}
			if (after == null)
				return;
			for (Node node : nodes)
				node.setComment(null);
			after.setComment(comment);
		});
	}

	/** Whether a comment of {@code node} is printed on lines of its own, not inside a line. */
	private static boolean onLinesOfItsOwn(Node node) {
		if (node instanceof ExpressionStmt && node.getParentNode().filter(LambdaExpr.class::isInstance).isPresent())
			return false;
		return node instanceof Statement || node instanceof BodyDeclaration<?> || node instanceof ImportDeclaration
				|| node instanceof PackageDeclaration || node instanceof ModuleDeclaration || node instanceof ModuleDirective
				|| node instanceof CompilationUnit || node instanceof SwitchEntry || node instanceof CatchClause;
	}

	/**
	 * JavaParser's printer puts an empty line before every member of a type - including the first,
	 * so every class body started with an empty line. Members are separated by one here instead.
	 * Comments: see the class.
	 */
	static final class Visitor extends DefaultPrettyPrinterVisitor {
		Visitor(PrinterConfiguration configuration) {
			super(configuration);
		}

		@Override
		protected void printMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
			for (int i = 0; i < members.size(); i++) {
				if (i > 0)
					this.printer.println();
				members.get(i).accept(this, arg);
				this.printer.println();
			}
		}

		/** A block comment inside a line ({@code case /* c *&#47; 1:}) is followed by a space, not a line break. */
		@Override
		public void visit(BlockComment n, Void arg) {
			Node commented = n.getCommentedNode().orElse(null);
			if (commented == null || onLinesOfItsOwn(commented)) {
				super.visit(n, arg);
				return;
			}
			printInline(n);
			this.printer.print(" ");
		}

		private void printInline(BlockComment n) {
			// as JavaParser does, without the line break after it
			String[] lines = n.getContent().split("\\R", -1);
			this.printer.print(n.getHeader());
			for (int i = 0; i < lines.length - 1; i++) {
				this.printer.print(lines[i]);
				this.printer.print(getOption(ConfigOption.END_OF_LINE_CHARACTER).get().asString());
			}
			this.printer.print(lines[lines.length - 1]);
			this.printer.print(n.getFooter());
		}

		/** The comments in an empty argument list ({@code g(/* nothing *&#47;)}), which belong to no argument. */
		@Override
		protected <T extends Expression> void printArguments(NodeList<T> args, Void arg) {
			Node owner = args.getParentNode().orElse(null);
			if (!args.isEmpty() || owner == null || owner.getOrphanComments().isEmpty()) {
				super.printArguments(args, arg);
				return;
			}
			this.printer.print("(");
			List<Comment> comments = new ArrayList<>(owner.getOrphanComments());
			comments.sort((a, b) -> a.getRange().isPresent() && b.getRange().isPresent() ? a.getRange().get().begin.compareTo(b.getRange().get().begin) : 0);
			for (int i = 0; i < comments.size(); i++) {
				Comment comment = comments.get(i);
				if (comment instanceof BlockComment block) {
					printInline(block);
					if (i + 1 < comments.size())
						this.printer.print(" ");
				} else {
					comment.accept(this, arg);
				}
			}
			this.printer.print(")");
		}

		// JavaParser's printer leaves out the comments of module directives

		@Override
		public void visit(ModuleRequiresDirective n, Void arg) {
			printComment(n.getComment(), arg);
			super.visit(n, arg);
		}

		@Override
		public void visit(ModuleExportsDirective n, Void arg) {
			printComment(n.getComment(), arg);
			super.visit(n, arg);
		}

		@Override
		public void visit(ModuleOpensDirective n, Void arg) {
			printComment(n.getComment(), arg);
			super.visit(n, arg);
		}

		@Override
		public void visit(ModuleProvidesDirective n, Void arg) {
			printComment(n.getComment(), arg);
			super.visit(n, arg);
		}

		@Override
		public void visit(ModuleUsesDirective n, Void arg) {
			printComment(n.getComment(), arg);
			super.visit(n, arg);
		}

		/** A node's leading comments (see LEADING_COMMENTS), before its own comment. */
		@Override
		protected void printOrphanCommentsBeforeThisChildNode(Node node) {
			super.printOrphanCommentsBeforeThisChildNode(node);
			if (node.containsData(LEADING_COMMENTS))
				for (Comment comment : node.getData(LEADING_COMMENTS))
					comment.accept(this, null);
		}

		/**
		 * The comments at the end of a switch entry - JavaParser's printer leaves them out: comments
		 * after an entry's last statement, and commented-out case labels (kept with the last entry).
		 */
		@Override
		public void visit(SwitchEntry n, Void arg) {
			super.visit(n, arg);
			printOrphanCommentsEnding(n);
		}

		/** The comment before a lambda's expression body ({@code () -> /* c *&#47; g(1)}). */
		@Override
		public void visit(LambdaExpr n, Void arg) {
			if (!(n.getBody() instanceof ExpressionStmt body) || body.getComment().isEmpty()) {
				super.visit(n, arg);
				return;
			}
			printOrphanCommentsBeforeThisChildNode(n);
			printComment(n.getComment(), arg);
			boolean parentheses = n.isEnclosingParameters();
			if (parentheses)
				this.printer.print("(");
			for (int i = 0; i < n.getParameters().size(); i++) {
				if (i > 0)
					this.printer.print(", ");
				n.getParameters().get(i).accept(this, arg);
			}
			if (parentheses)
				this.printer.print(")");
			this.printer.print(" -> ");
			printComment(body.getComment(), arg);
			body.getExpression().accept(this, arg);
		}
	}
}
