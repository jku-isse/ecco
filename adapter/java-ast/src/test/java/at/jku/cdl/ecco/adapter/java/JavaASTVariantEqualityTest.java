package at.jku.cdl.ecco.adapter.java;

import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTConstructorData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTModuleData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTTryData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two variants of a file that differ in one aspect of a constructor, a try statement or a module
 * declaration, read by the reader: whether the resulting artifact data are equal decides whether
 * ECCO keeps both versions apart (unequal) or treats them as the same artifact (equal). Then whole
 * repositories: each variant committed under its own feature must check out its own version.
 */
public class JavaASTVariantEqualityTest {

	private final JavaASTReader reader = new JavaASTReader(new SerEntityFactory());

	private Node.Op read(String fileName, String source) throws IOException {
		Path baseDir = Files.createTempDirectory("java-ast-variant-equality");
		Files.writeString(baseDir.resolve(fileName), source);
		Set<Node.Op> nodes = reader.read(baseDir, new Path[]{Path.of(fileName)});
		return nodes.iterator().next();
	}

	private static <T extends ArtifactData> T find(Node node, Class<T> type) {
		if (node.getArtifact() != null && type.isInstance(node.getArtifact().getData()))
			return type.cast(node.getArtifact().getData());
		for (Node child : node.getChildren()) {
			T found = find(child, type);
			if (found != null)
				return found;
		}
		return null;
	}

	private <T extends ArtifactData> T readFirst(String fileName, String source, Class<T> type) throws IOException {
		T data = find(read(fileName, source), type);
		assertNotNull(data, "no " + type.getSimpleName() + " in " + source);
		return data;
	}

	private JavaASTConstructorData constructorOf(String constructor) throws IOException {
		return readFirst("Foo.java", "import java.io.*;\nclass Foo {\n    " + constructor + "\n}\n", JavaASTConstructorData.class);
	}

	private JavaASTTryData tryOf(String tryStatement) throws IOException {
		return readFirst("Foo.java", "class Foo {\n    void m() throws Exception {\n        " + tryStatement + "\n    }\n}\n", JavaASTTryData.class);
	}

	private JavaASTModuleData moduleOf(String module) throws IOException {
		return readFirst("module-info.java", module, JavaASTModuleData.class);
	}

	private static void assertSame(ArtifactData a, ArtifactData b) {
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	// ---- constructors ----

	@Test
	public void constructorThrowsClauseDistinguishes() throws IOException {
		assertNotEquals(constructorOf("Foo() throws IOException { }"), constructorOf("Foo() throws java.sql.SQLException { }"));
		assertNotEquals(constructorOf("Foo() throws IOException { }"), constructorOf("Foo() { }"));
	}

	@Test
	public void constructorParametersDistinguish() throws IOException {
		assertNotEquals(constructorOf("Foo(int x) { }"), constructorOf("Foo(long x) { }"));
		assertNotEquals(constructorOf("Foo(int x) { }"), constructorOf("Foo(int y) { }"));
		assertNotEquals(constructorOf("Foo(int x) { }"), constructorOf("Foo(final int x) { }"));
		assertNotEquals(constructorOf("Foo(int x, int y) { }"), constructorOf("Foo(int y, int x) { }"));
		assertNotEquals(constructorOf("Foo(int... x) { }"), constructorOf("Foo(int[] x) { }"));
	}

	@Test
	public void constructorAnnotationsDistinguish() throws IOException {
		assertNotEquals(constructorOf("@Deprecated Foo() { }"), constructorOf("Foo() { }"));
		assertNotEquals(constructorOf("@SuppressWarnings(\"a\") Foo() { }"), constructorOf("@SuppressWarnings(\"b\") Foo() { }"));
	}

	@Test
	public void constructorTypeParametersDistinguish() throws IOException {
		assertNotEquals(constructorOf("<T> Foo(T t) { }"), constructorOf("<U> Foo(T t) { }"));
		assertNotEquals(constructorOf("<T extends Number> Foo() { }"), constructorOf("<T> Foo() { }"));
	}

	@Test
	public void constructorModifiersDistinguish() throws IOException {
		assertNotEquals(constructorOf("public Foo() { }"), constructorOf("private Foo() { }"));
		assertNotEquals(constructorOf("public Foo() { }"), constructorOf("Foo() { }"));
	}

	@Test
	public void constructorsDifferingOnlyInLayoutCommentsOrBodyAreTheSame() throws IOException {
		JavaASTConstructorData plain = constructorOf("public Foo(int x) throws IOException { }");
		assertSame(plain, constructorOf("public   Foo( int  x )\n        throws   IOException {\n    }"));
		assertSame(plain, constructorOf("/** Doc. */ public Foo(int x) throws IOException { /* empty */ }"));
		// the body's statements are children of their own, not part of the constructor's identity
		assertSame(plain, constructorOf("public Foo(int x) throws IOException { System.out.println(x); }"));
	}

	@Test
	public void recordCompactConstructorIsNotTheCanonicalOne() throws IOException {
		JavaASTConstructorData compact = readFirst("P.java", "record P(int x) {\n    P { }\n}\n", JavaASTConstructorData.class);
		JavaASTConstructorData canonical = readFirst("P.java", "record P(int x) {\n    P(int x) { this.x = x; }\n}\n", JavaASTConstructorData.class);
		assertTrue(compact.isCompact());
		assertNotEquals(compact, canonical);
	}

	// ---- try statements ----

	@Test
	public void tryResourcesDistinguish() throws IOException {
		assertNotEquals(tryOf("try (Reader r = open()) { }"), tryOf("try (Writer w = open()) { }"));
		assertNotEquals(tryOf("try (Reader r = open()) { } finally { }"), tryOf("try { } finally { }"));
	}

	@Test
	public void tryCatchTypesDistinguishWithoutResources() throws IOException {
		assertNotEquals(tryOf("try { a(); } catch (IOException e) { }"), tryOf("try { a(); } catch (RuntimeException e) { }"));
		assertNotEquals(tryOf("try { a(); } catch (IOException e) { }"), tryOf("try { a(); } catch (IOException | RuntimeException e) { }"));
	}

	/**
	 * Documents current behaviour: with resources, the reader leaves the catch types out of the try's
	 * data (JavaASTReader.addTryStatement, "if (!hasExpression)"), so these two try statements are
	 * the same artifact. Without resources they are not (see above). No code is lost by it - each
	 * catch clause is a CATCHCLAUSE child with its own type, see
	 * tryWithResourcesDifferingInCatchTypeCheckOutTheirOwnCatch - but the rule is inconsistent.
	 */
	@Test
	public void tryCatchTypesDoNotDistinguishWithResources() throws IOException {
		assertSame(tryOf("try (Reader r = open()) { } catch (IOException e) { }"),
				tryOf("try (Reader r = open()) { } catch (RuntimeException e) { }"));
	}

	@Test
	public void tryFinallyPresenceDistinguishesButNotItsContent() throws IOException {
		assertNotEquals(tryOf("try { a(); } catch (IOException e) { } finally { b(); }"), tryOf("try { a(); } catch (IOException e) { }"));
		// the finally block's content is a FINALLY child of its own
		assertSame(tryOf("try { a(); } finally { b(); }"), tryOf("try { a(); } finally { c(); }"));
		// the try body's statements are children of their own
		assertSame(tryOf("try { a(); } finally { b(); }"), tryOf("try { z(); } finally { b(); }"));
	}

	// ---- modules ----

	@Test
	public void moduleNameAndOpenFlagDistinguish() throws IOException {
		assertNotEquals(moduleOf("module a.b { }\n"), moduleOf("module a.c { }\n"));
		assertNotEquals(moduleOf("module a.b { }\n"), moduleOf("open module a.b { }\n"));
	}

	@Test
	public void moduleDirectivesAreChildrenNotPartOfTheModule() throws IOException {
		assertSame(moduleOf("module a.b {\n    requires java.base;\n}\n"), moduleOf("module a.b {\n    requires java.sql;\n}\n"));
	}

	// ---- node types with the same text ----

	/**
	 * A simple string's node type is not part of its identity, and the class node's children are
	 * unordered (duplicates rejected by equality): an enum with a constant named FIELDS and a field
	 * had an ENUM_CONSTANTS "FIELDS" child and a FIELD_GROUP "FIELDS" child that compared equal, and
	 * reading failed with "An equivalent child is already contained". The constants are in a group
	 * of their own now.
	 */
	@Test
	public void enumWithAConstantNamedFieldsAndAFieldCanBeRead() throws IOException {
		Node.Op file = read("E.java", "enum E {\n    FIELDS;\n    int x;\n}\n");
		assertNotNull(file);
	}

	private static final String TWO_EQUAL_INITIALIZERS = "class C {\n    static int x;\n    static {\n        x++;\n    }\n    static {\n        x++;\n    }\n}\n";

	/**
	 * Two initializer blocks with the same text (legal Java) were equal artifacts under the class
	 * node, whose children must differ: reading the file failed ("An equivalent child is already
	 * contained"), and it was committed by the text adapter (gap #64). The second is told apart by
	 * its occurrence; both are written back; a class with one such block is read as before.
	 */
	@Test
	public void classWithTwoEqualInitializersCanBeRead() throws IOException {
		assertNotNull(read("C.java", TWO_EQUAL_INITIALIZERS));
		String written = JavaASTRoundTripTest.readWrite("C.java", TWO_EQUAL_INITIALIZERS);
		assertEquals(2, written.split("static \\{", -1).length - 1, written);
		assertEquals(2, written.split("x\\+\\+;", -1).length - 1, written);
	}

	@Test
	public void aSingleInitializerIsReadAsBefore() throws IOException {
		String one = "class C {\n    static int x;\n    static {\n        x++;\n    }\n}\n";
		java.util.List<Object> data = new java.util.ArrayList<>();
		collectData(read("C.java", one), data);
		assertTrue(data.stream().noneMatch(d -> d instanceof at.jku.cdl.ecco.adapter.java.artifactData.JavaASTRepeatedData), data.toString());
	}

	private static void collectData(at.jku.isse.ecco.tree.Node node, java.util.List<Object> data) {
		if (node.getArtifact() != null)
			data.add(node.getArtifact().getData());
		for (at.jku.isse.ecco.tree.Node child : node.getChildren())
			collectData(child, data);
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null)
			t = t.getCause();
		return t.getMessage();
	}

	// ---- whole repositories ----

	private static String[] commitAndCheckOut(String fileName, String variantA, String variantB) throws Exception {
		Path work = Files.createTempDirectory("java-ast-variant-repo");
		Path content = Files.createDirectories(work.resolve("content"));
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(work.resolve(".ecco"));
			service.init();
			service.setBaseDir(content);
			Files.writeString(content.resolve(fileName), variantA);
			service.commit("A", "A");
			Files.writeString(content.resolve(fileName), variantB);
			service.commit("B", "B");

			String[] result = new String[2];
			String[] configurations = {"A", "B"};
			for (int i = 0; i < 2; i++) {
				Path checkout = Files.createDirectories(work.resolve("checkout-" + configurations[i]));
				service.setBaseDir(checkout);
				service.checkout(configurations[i]);
				result[i] = Files.readString(checkout.resolve(fileName));
			}
			return result;
		}
	}

	@Test
	@Timeout(120)
	public void constructorsDifferingInThrowsCheckOutTheirOwnClause() throws Exception {
		String a = "class Foo {\n\n    Foo() throws java.io.IOException {\n        init();\n    }\n}\n";
		String b = "class Foo {\n\n    Foo() throws java.sql.SQLException {\n        init();\n    }\n}\n";
		String[] out = commitAndCheckOut("Foo.java", a, b);
		assertTrue(out[0].contains("IOException") && !out[0].contains("SQLException"), out[0]);
		assertTrue(out[1].contains("SQLException") && !out[1].contains("IOException"), out[1]);
		assertTrue(out[0].contains("init();") && out[1].contains("init();"));
	}

	@Test
	@Timeout(120)
	public void tryStatementsDifferingInCatchTypeCheckOutTheirOwnCatch() throws Exception {
		String a = "class Foo {\n\n    void m() {\n        try {\n            a();\n        } catch (IllegalStateException e) {\n            b();\n        }\n    }\n}\n";
		String b = a.replace("IllegalStateException", "IllegalArgumentException");
		String[] out = commitAndCheckOut("Foo.java", a, b);
		assertTrue(out[0].contains("IllegalStateException") && !out[0].contains("IllegalArgumentException"), out[0]);
		assertTrue(out[1].contains("IllegalArgumentException") && !out[1].contains("IllegalStateException"), out[1]);
	}

	@Test
	@Timeout(120)
	public void tryWithResourcesDifferingInCatchTypeCheckOutTheirOwnCatch() throws Exception {
		String a = "class Foo {\n\n    void m() {\n        try (java.io.Reader r = open()) {\n            a();\n        } catch (IllegalStateException e) {\n            b();\n        }\n    }\n}\n";
		String b = a.replace("IllegalStateException", "IllegalArgumentException");
		String[] out = commitAndCheckOut("Foo.java", a, b);
		assertTrue(out[0].contains("IllegalStateException") && !out[0].contains("IllegalArgumentException"), out[0]);
		assertTrue(out[1].contains("IllegalArgumentException") && !out[1].contains("IllegalStateException"), out[1]);
	}

	@Test
	@Timeout(120)
	public void modulesDifferingInADirectiveCheckOutTheirOwnDirective() throws Exception {
		String a = "module a.b {\n    requires java.sql;\n}\n";
		String b = "module a.b {\n    requires java.xml;\n}\n";
		String[] out = commitAndCheckOut("module-info.java", a, b);
		assertEquals(a, out[0]);
		assertEquals(b, out[1]);
	}
}
