package at.jku.cdl.ecco.adapter.java.artifactData;

import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;

import java.io.Serializable;
import java.util.Objects;

/** A comment of the source, kept with the artifact of the node it belongs to (see JavaASTData). */
public final class JavaASTComment implements Serializable {

	private static final long serialVersionUID = 1L;

	private enum Kind {
		LINE, BLOCK, JAVADOC
	}

	private final Kind kind;
	private final String content;

	private JavaASTComment(Kind kind, String content) {
		this.kind = kind;
		this.content = content;
	}

	public static JavaASTComment of(Comment comment) {
		Kind kind = comment instanceof JavadocComment ? Kind.JAVADOC : comment instanceof BlockComment ? Kind.BLOCK : Kind.LINE;
		return new JavaASTComment(kind, comment.getContent());
	}

	public Comment toComment() {
		return switch (this.kind) {
			case JAVADOC -> new JavadocComment(this.content);
			case BLOCK -> new BlockComment(this.content);
			case LINE -> new LineComment(this.content);
		};
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof JavaASTComment other && this.kind == other.kind && this.content.equals(other.content);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.kind, this.content);
	}

	@Override
	public String toString() {
		return this.toComment().toString();
	}
}
