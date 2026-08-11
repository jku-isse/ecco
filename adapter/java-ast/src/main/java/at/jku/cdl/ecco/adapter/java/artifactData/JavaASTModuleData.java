package at.jku.cdl.ecco.adapter.java.artifactData;

/**
 * A module-info.java's module header ("module com.example.foo" / "open module com.example.foo") -
 * structured rather than opaque text (unlike most other AST node types here) since the writer needs
 * the name and open-flag back out separately to reconstruct a com.github.javaparser.ast.modules.ModuleDeclaration.
 * Its directives (requires/exports/opens/uses/provides) are separate MODULE_DIRECTIVE children,
 * captured as opaque text the same way IMPORT_DECLARATION is - see JavaASTReader/JavaASTWriteHandler.
 */
public class JavaASTModuleData extends JavaASTData {

	private static final long serialVersionUID = 1L;

	private final String name;
	private final boolean open;

	public JavaASTModuleData(String name, boolean open) {
		this.name = name;
		this.open = open;
		this.setType(ASTNodeType.MODULE_DECLARATION);
	}

	public String getName() {
		return name;
	}

	public boolean isOpen() {
		return open;
	}

	@Override
	public String toString() {
		return (open ? "open module " : "module ") + name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (open ? 1231 : 1237);
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		JavaASTModuleData other = (JavaASTModuleData) obj;
		if (open != other.open)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
