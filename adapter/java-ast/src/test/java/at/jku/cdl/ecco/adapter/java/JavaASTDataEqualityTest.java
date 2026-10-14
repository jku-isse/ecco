package at.jku.cdl.ecco.adapter.java;

import at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTComment;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTConstructorData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTModuleData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTSimpleStringData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTTryData;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ECCO matches artifacts across variants by the equals/hashCode of their data: two artifacts that
 * differ in meaning but compare equal make one variant's code replace the other's, two equal ones
 * that compare unequal are stored twice. These tests pin down, field by field, what is part of the
 * identity of the Java AST adapter's artifact data and what deliberately is not (comments, block
 * shape and the text with inner comments are metadata, see JavaASTData).
 */
public class JavaASTDataEqualityTest {

	/** Reflexive, symmetric, unequal to null and to other types, hashCode consistent. */
	private static void assertContract(Object a, Object equalToA) {
		assertEquals(a, a);
		assertEquals(a, equalToA);
		assertEquals(equalToA, a);
		assertEquals(a.hashCode(), equalToA.hashCode());
		assertNotEquals(null, a);
		assertFalse(a.equals(null));
		assertFalse(a.equals("not artifact data"));
	}

	private static void assertDiffer(Object a, Object b) {
		assertNotEquals(a, b);
		assertNotEquals(b, a);
	}

	// ---- constructor ----

	private static JavaASTConstructorData constructor(Consumer<JavaASTConstructorData> setup) {
		return constructor("Foo", setup);
	}

	private static JavaASTConstructorData constructor(String name, Consumer<JavaASTConstructorData> setup) {
		JavaASTConstructorData data = new JavaASTConstructorData(name);
		data.addModifier("public");
		data.addParameter("int x");
		data.addTypeParameter("T");
		data.addThrowException("IOException");
		data.addAnnotation("@Deprecated");
		setup.accept(data);
		return data;
	}

	@Test
	public void constructorContract() {
		assertContract(constructor(d -> {}), constructor(d -> {}));
		assertFalse(constructor(d -> {}).equals(new JavaASTSimpleStringData("Foo")));
	}

	@Test
	public void constructorsDifferingInOneFieldAreUnequal() {
		JavaASTConstructorData base = constructor(d -> {});
		assertDiffer(base, constructor("Bar", d -> {}));
		assertDiffer(base, constructor(d -> d.addModifier("final")));
		assertDiffer(base, constructor(d -> d.addParameter("int y")));
		assertDiffer(base, constructor(d -> d.addTypeParameter("U")));
		assertDiffer(base, constructor(d -> d.addThrowException("SQLException")));
		assertDiffer(base, constructor(d -> d.addAnnotation("@SuppressWarnings(\"x\")")));
		assertDiffer(base, constructor(d -> d.setCompact(true)));
	}

	@Test
	public void constructorEqualityDoesNotDependOnCommentsOrBodyShape() {
		JavaASTConstructorData a = constructor(d -> {});
		JavaASTConstructorData b = constructor(d -> {
			d.setComment(JavaASTComment.of(new JavadocComment("Doc.")));
			d.setOrphanComments(List.of(JavaASTComment.of(new LineComment("orphan"))));
			d.setBlockBody(Boolean.FALSE);
			d.setTextWithComments("with comments");
		});
		assertContract(a, b);
	}

	@Test
	public void constructorAddersIgnoreNullAndEmpty() {
		JavaASTConstructorData data = new JavaASTConstructorData("Foo");
		assertFalse(data.addModifier(null));
		assertFalse(data.addModifier(""));
		assertFalse(data.addParameter(null));
		assertFalse(data.addParameter(""));
		assertFalse(data.addTypeParameter(null));
		assertFalse(data.addTypeParameter(""));
		assertEquals(Boolean.FALSE, data.addThrowException(null));
		assertEquals(Boolean.FALSE, data.addThrowException(""));
		assertFalse(data.addAnnotation(null));
		assertFalse(data.addAnnotation(""));
		assertEquals(new JavaASTConstructorData("Foo"), data);
		assertTrue(data.getModifiers().isEmpty() && data.getParameters().isEmpty() && data.getTypeParameters().isEmpty()
				&& data.getThrowExceptions().isEmpty() && data.getAnnotations().isEmpty());
		assertEquals("Foo", data.getName());
		assertFalse(data.isCompact());
		assertThrows(UnsupportedOperationException.class, () -> data.getParameters().add("int x"));
	}

	@Test
	public void constructorParameterOrderCounts() {
		JavaASTConstructorData ab = new JavaASTConstructorData("Foo");
		ab.addParameter("int a");
		ab.addParameter("int b");
		JavaASTConstructorData ba = new JavaASTConstructorData("Foo");
		ba.addParameter("int b");
		ba.addParameter("int a");
		assertDiffer(ab, ba);
	}

	/**
	 * Documents current behaviour: the throws clause is a list, so "throws A, B" and "throws B, A"
	 * are different artifacts although they mean the same. Harmless (at worst the constructor is
	 * stored twice for such variants), noted for completeness.
	 */
	@Test
	public void constructorThrowsOrderCountsAlthoughItDoesNotMatterToJava() {
		JavaASTConstructorData ab = new JavaASTConstructorData("Foo");
		ab.addThrowException("A");
		ab.addThrowException("B");
		JavaASTConstructorData ba = new JavaASTConstructorData("Foo");
		ba.addThrowException("B");
		ba.addThrowException("A");
		assertDiffer(ab, ba);
	}

	// ---- try ----

	private static JavaASTTryData tryData(List<String> resources, List<String> catches, boolean hasFinally) {
		JavaASTTryData data = new JavaASTTryData();
		resources.forEach(data::addExpression);
		catches.forEach(data::addCatchParam);
		data.setFinally(hasFinally);
		return data;
	}

	@Test
	public void tryContract() {
		assertContract(tryData(List.of("R r = r()"), List.of("IOException e"), true),
				tryData(List.of("R r = r()"), List.of("IOException e"), true));
		assertFalse(new JavaASTTryData().equals(new JavaASTSimpleStringData("try")));
		assertEquals(ASTNodeType.TRYBLOCK, new JavaASTTryData().getType());
	}

	@Test
	public void tryDataDifferingInOneFieldAreUnequal() {
		JavaASTTryData base = tryData(List.of("R r = r()"), List.of("IOException e"), true);
		assertDiffer(base, tryData(List.of("S s = s()"), List.of("IOException e"), true));
		assertDiffer(base, tryData(List.of(), List.of("IOException e"), true));
		assertDiffer(base, tryData(List.of("R r = r()"), List.of("SQLException e"), true));
		assertDiffer(base, tryData(List.of("R r = r()"), List.of(), true));
		assertDiffer(base, tryData(List.of("R r = r()"), List.of("IOException e"), false));
	}

	@Test
	public void tryAddersIgnoreNullAndEmpty() {
		JavaASTTryData data = new JavaASTTryData();
		data.addExpression(null);
		data.addExpression("");
		data.addCatchParam(null);
		data.addCatchParam("");
		assertEquals(new JavaASTTryData(), data);
		assertTrue(data.getExpressions().isEmpty() && data.getCatchParams().isEmpty());
		assertFalse(data.hasFinally());
	}

	@Test
	public void tryEqualityDoesNotDependOnComments() {
		JavaASTTryData a = tryData(List.of(), List.of("IOException e"), false);
		JavaASTTryData b = tryData(List.of(), List.of("IOException e"), false);
		b.setComment(JavaASTComment.of(new LineComment("why")));
		assertContract(a, b);
	}

	// ---- module ----

	@Test
	public void moduleContract() {
		assertContract(new JavaASTModuleData("com.example", false), new JavaASTModuleData("com.example", false));
		assertContract(new JavaASTModuleData(null, true), new JavaASTModuleData(null, true));
		assertFalse(new JavaASTModuleData("com.example", false).equals(new JavaASTSimpleStringData("com.example")));
	}

	@Test
	public void modulesDifferingInOneFieldAreUnequal() {
		JavaASTModuleData base = new JavaASTModuleData("com.example", false);
		assertDiffer(base, new JavaASTModuleData("com.other", false));
		assertDiffer(base, new JavaASTModuleData("com.example", true));
		assertDiffer(base, new JavaASTModuleData(null, false));
		assertEquals("module com.example", base.toString());
		assertEquals("open module com.example", new JavaASTModuleData("com.example", true).toString());
	}

	// ---- simple string ----

	@Test
	public void simpleStringContract() {
		assertContract(new JavaASTSimpleStringData("x();"), new JavaASTSimpleStringData("x();"));
		assertDiffer(new JavaASTSimpleStringData("x();"), new JavaASTSimpleStringData("y();"));
	}

	@Test
	public void simpleStringLineEndingsDoNotCountButOtherWhitespaceDoes() {
		assertContract(new JavaASTSimpleStringData("a\r\nb"), new JavaASTSimpleStringData("a\nb"));
		assertContract(new JavaASTSimpleStringData("a\rb"), new JavaASTSimpleStringData("a\nb"));
		// a text block's content is its line structure, so a changed newline is a change
		assertDiffer(new JavaASTSimpleStringData("a\nb"), new JavaASTSimpleStringData("a b"));
	}

	@Test
	public void simpleStringEqualityDoesNotDependOnCommentsBlockShapeOrInnerComments() {
		JavaASTSimpleStringData a = new JavaASTSimpleStringData("for (;;) x();");
		JavaASTSimpleStringData b = new JavaASTSimpleStringData("for (;;) x();");
		b.setComment(JavaASTComment.of(new BlockComment("c")));
		b.setBlockBody(Boolean.TRUE);
		b.setTextWithComments("for (;;) /* c */ x();");
		assertContract(a, b);
	}

	/**
	 * Documents current behaviour: the node type is not part of a simple string's identity (its
	 * equals/hashCode override JavaASTData's without calling it). Two artifacts of different kinds
	 * with the same text compare equal - see JavaASTVariantEqualityTest for where that bites.
	 */
	@Test
	public void simpleStringTypeIsNotPartOfIdentity() {
		JavaASTSimpleStringData group = new JavaASTSimpleStringData("FIELDS");
		group.setType(ASTNodeType.FIELD_GROUP);
		JavaASTSimpleStringData constant = new JavaASTSimpleStringData("FIELDS");
		constant.setType(ASTNodeType.ENUM_CONSTANTS);
		assertEquals(group, constant);
		assertEquals(group.hashCode(), constant.hashCode());
	}

	@Test
	public void simpleStringNullTextComparesByNull() {
		// unformattedString rejects null, so a null text cannot be constructed
		assertThrows(NullPointerException.class, () -> new JavaASTSimpleStringData(null));
	}

	// ---- base class ----

	private static final class Plain extends JavaASTData {
		private static final long serialVersionUID = 1L;
	}

	private static final class OtherPlain extends JavaASTData {
		private static final long serialVersionUID = 1L;
	}

	@Test
	public void baseEqualityIsTheTypeAndTheClass() {
		Plain a = new Plain();
		Plain b = new Plain();
		assertContract(a, b);
		assertEquals(ASTNodeType.UNKNOWN, a.getType());
		b.setType(ASTNodeType.STATEMENT);
		assertDiffer(a, b);
		// getClass() equality: the same type in another subclass is not equal
		assertDiffer(new Plain(), new OtherPlain());
		assertThrows(IllegalArgumentException.class, () -> a.setType(null));
		assertEquals("UNKNOWN", a.toString());
	}

	@Test
	public void baseMetadataIsAdoptedNotCompared() {
		Plain a = new Plain();
		assertTrue(a.getOrphanComments().isEmpty());
		a.setOrphanComments(List.of());
		assertTrue(a.getOrphanComments().isEmpty());
		Plain newer = new Plain();
		newer.setComment(JavaASTComment.of(new LineComment("c")));
		newer.setOrphanComments(List.of(JavaASTComment.of(new LineComment("o"))));
		newer.setBlockBody(Boolean.TRUE);
		newer.setTextWithComments("t");
		assertEquals(a, newer);
		a.adoptMetadataFrom(newer);
		assertEquals(newer.getComment(), a.getComment());
		assertEquals(newer.getOrphanComments(), a.getOrphanComments());
		assertEquals(Boolean.TRUE, a.getBlockBody());
		assertEquals("t", a.getTextWithComments());
		// not Java AST data: nothing adopted
		a.adoptMetadataFrom(null);
		assertEquals("t", a.getTextWithComments());
	}

	@Test
	public void retiredFormatOnlyForOldPackageDeclarations() {
		JavaASTSimpleStringData pack = new JavaASTSimpleStringData("p");
		pack.setType(ASTNodeType.PACKAGEDECLARATION);
		assertNotNull(pack.retiredFormat());
		pack.setTreeFormat(JavaASTData.TREE_FORMAT);
		assertNull(pack.retiredFormat());
		// the tree format is not part of identity: an old and a new package declaration match
		JavaASTSimpleStringData old = new JavaASTSimpleStringData("p");
		old.setType(ASTNodeType.PACKAGEDECLARATION);
		assertEquals(old, pack);
		assertNull(new JavaASTSimpleStringData("x();").retiredFormat());
	}

	// ---- comment ----

	@Test
	public void commentContract() {
		Supplier<JavaASTComment> line = () -> JavaASTComment.of(new LineComment(" x"));
		assertContract(line.get(), line.get());
		assertDiffer(line.get(), JavaASTComment.of(new BlockComment(" x")));
		assertDiffer(line.get(), JavaASTComment.of(new JavadocComment(" x")));
		assertDiffer(JavaASTComment.of(new BlockComment(" x")), JavaASTComment.of(new JavadocComment(" x")));
		assertDiffer(line.get(), JavaASTComment.of(new LineComment(" y")));
	}

	@Test
	public void commentKindSurvivesConversionBack() {
		assertInstanceOf(LineComment.class, JavaASTComment.of(new LineComment("a")).toComment());
		assertInstanceOf(BlockComment.class, JavaASTComment.of(new BlockComment("a")).toComment());
		assertInstanceOf(JavadocComment.class, JavaASTComment.of(new JavadocComment("a")).toComment());
		assertEquals("/*a*/", JavaASTComment.of(new BlockComment("a")).toString().trim());
	}
}
