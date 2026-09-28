package at.jku.isse.ecco.adapter.cpp.data;

/**
 * The artifact data classes of the C++ adapter's first version, which split a file into includes,
 * defines, fields and functions (with their statement blocks) and wrote it back in that order,
 * dropping comments, namespaces, classes and conditional directives. They are still read and written
 * so repositories holding them can be checked out; committing to such a repository is refused.
 */
final class RetiredFormat {

	static final String EXPLANATION = "its C++ files are stored in the first C++ adapter's format, which did not keep"
			+ " them as they are (comments, namespaces, classes and #if directives were lost; the rest was reordered).";

	private RetiredFormat() {
	}
}
