package at.jku.cdl.ecco.adapter.java.artifactData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import at.jku.isse.ecco.artifact.ArtifactData;

public abstract class JavaASTData implements ArtifactData, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ASTNodeType type = ASTNodeType.UNKNOWN;

	/**
	 * The comment JavaParser attributed to the node (e.g. a method's Javadoc, or a line comment above
	 * or behind a statement), and comments inside the node attributed to no node of their own (e.g.
	 * one at the end of a method body). Not part of the artifact's identity: a changed comment does
	 * not make a new artifact, the newest commit's comments are kept (adoptMetadataFrom). Before
	 * they were kept, every checkout lost all comments of the file.
	 */
	private JavaASTComment comment;
	private List<JavaASTComment> orphanComments;

	/**
	 * For a node with a body (loop, if condition, else branch): whether the body was written as a
	 * block ({@code for (...) { x(); }}) or as a single statement ({@code for (...) x();}). Like the
	 * comments, not part of the artifact's identity. Null in repositories committed before it was
	 * kept: the writer then chooses as it always did.
	 */
	private Boolean blockBody;

	/**
	 * For a statement with comments inside it (in a lambda body, between the operands of an
	 * expression): its text with those comments, which the identifying text leaves out. Like the
	 * comments above, not part of the artifact's identity and taken from the newest commit; null when
	 * the statement has no inner comments, and in repositories committed before it was kept (the
	 * writer then writes the text without them, as it always did).
	 */
	private String textWithComments;

	/**
	 * How the reader builds a file's tree: 1 since synchronized statements and blocks among
	 * statements are nodes of their own (they used to be dropped / flattened into the enclosing
	 * statements) and arrow switch entries are told apart from "case A:" ones. Kept on the file's package declaration; 0 (absent) in repositories committed
	 * before, whose trees cannot be combined with the new ones - see retiredFormat().
	 */
	public static final int TREE_FORMAT = 1;
	private int treeFormat;

	public JavaASTComment getComment() {
		return this.comment;
	}

	public void setComment(JavaASTComment comment) {
		this.comment = comment;
	}

	public List<JavaASTComment> getOrphanComments() {
		return this.orphanComments == null ? List.of() : this.orphanComments;
	}

	public void setOrphanComments(List<JavaASTComment> orphanComments) {
		this.orphanComments = orphanComments == null || orphanComments.isEmpty() ? null : new ArrayList<>(orphanComments);
	}

	public Boolean getBlockBody() {
		return this.blockBody;
	}

	public void setBlockBody(Boolean blockBody) {
		this.blockBody = blockBody;
	}

	public String getTextWithComments() {
		return this.textWithComments;
	}

	public void setTextWithComments(String textWithComments) {
		this.textWithComments = textWithComments;
	}

	public void setTreeFormat(int treeFormat) {
		this.treeFormat = treeFormat;
	}

	@Override
	public String retiredFormat() {
		if (this.getType() == ASTNodeType.PACKAGEDECLARATION && this.treeFormat < TREE_FORMAT)
			return "its Java files are stored in a format that lost their synchronized statements, the blocks among"
					+ " their statements (e.g. case 0: { ... }) and the arrows of switch cases (case A -> ...).";
		return null;
	}

	@Override
	public void adoptMetadataFrom(ArtifactData newer) {
		if (newer instanceof JavaASTData other) {
			this.comment = other.comment;
			this.orphanComments = other.orphanComments;
			this.blockBody = other.blockBody;
			this.textWithComments = other.textWithComments;
		}
	}
	
	/**
	 * Used to collapse a captured fragment's tabs/newlines to spaces, which silently corrupted any
	 * construct where a newline is semantically or syntactically load-bearing - most notably text
	 * blocks, whose value IS its internal line structure, and whose opening delimiter requires a
	 * newline immediately after it. Now just normalizes line endings to plain "\n" (still needed
	 * since JavaParser's printer output could otherwise carry CRLF depending on platform) without
	 * discarding any of the original content.
	 */
	protected String unformattedString(String str) {
		return str.replace("\r\n", "\n").replace("\r", "\n");
	}
	
	public void setType(ASTNodeType type) {
		if(type != null) {
			this.type = type;
		} else {
			throw new IllegalArgumentException("Type can not be null!");
		}
	}
	
	public ASTNodeType getType() {
		return type;
	}

	@Override
	public String toString() {
		return type.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JavaASTData other = (JavaASTData) obj;
		if (type != other.type)
			return false;
		return true;
	}
	
	
	
}
