package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.service.EccoService;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Variants of one Java class, generated: imports, fields and methods of a shared pool - each method
 * with its own selection of a pool of statements, members with a line comment or Javadoc before them -
 * each variant with each of them or not. Committed under a feature each, every variant must check out
 * as committed and as it does from a repository holding it alone. The Java (AST) adapter pretty-prints
 * and writes a type's members and imports in the order their associations come
 * (JavaASTRepositoryRoundTripTest), so both are compared parsed, members and imports sorted: what the
 * adapter promises (AD-6), not its layout. A comment is metadata of the statement or member it is
 * before, not part of its identity: one both variants have carries the comment of the newest commit
 * (JavaASTAdoptedMetadataStoredTest) - so a variant alone keeps its comments, and together with
 * others its code.
 */
public class JavaASTVariantsRoundTripTest {

	private static final JavaParser PARSER = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
	private static final Path FILE = Path.of("src", "p", "C.java");
	private static final String[] IMPORTS = {"import java.util.List;", "import java.util.Map;", "import java.io.File;"};
	private static final String[] STATEMENTS = {"int x = 1;", "x++;", "System.out.println(\"hi\");", "if (x > 0) { x--; }",
			"for (int i = 0; i < 3; i++) { x += i; }", "// a statement comment", "return;"};

	/** a pool member: its lines, and for a method the statements of its body (null for a field) */
	private record Member(List<String> head, List<String> body) {
	}

	@Test
	@Timeout(900)
	public void variantsOfAClassComeBackAsCommittedAndAsAlone(@TempDir Path tmp) throws Exception {
		Random random = new Random(13);
		List<String> wrong = new ArrayList<>();
		for (int round = 0; round < 120 && wrong.size() < 3; round++) {
			List<String> imports = new ArrayList<>();
			for (String i : IMPORTS)
				if (random.nextBoolean())
					imports.add(i);
			List<Member> pool = new ArrayList<>();
			for (int m = 0; m < 2 + random.nextInt(5); m++) {
				List<String> head = new ArrayList<>();
				switch (random.nextInt(4)) {
					case 0 -> head.add("    // member " + m);
					case 1 -> head.addAll(List.of("    /**", "     * Member " + m + ".", "     */"));
					default -> {
					}
				}
				if (random.nextInt(3) == 0) {
					head.add("    private int f" + m + " = " + random.nextInt(9) + ";");
					pool.add(new Member(head, null));
				} else {
					head.add("    void m" + m + "() {");
					List<String> body = new ArrayList<>();
					for (int s = random.nextInt(5); s > 0; s--)
						body.add("        " + STATEMENTS[random.nextInt(STATEMENTS.length - 1)]);
					pool.add(new Member(head, body));
				}
			}
			List<String> variants = new ArrayList<>();
			for (int v = 2 + random.nextInt(3); v > 0; v--) {
				StringBuilder text = new StringBuilder("package p;\n\n");
				for (String i : imports)
					if (random.nextInt(10) < 7)
						text.append(i).append("\n");
				text.append("\npublic class C {\n");
				for (Member member : pool) {
					if (random.nextInt(10) >= 7)
						continue;
					for (String line : member.head())
						text.append(line).append("\n");
					if (member.body() != null) {
						for (String statement : member.body())
							if (random.nextInt(10) < 7)
								text.append(statement).append("\n");
						text.append("    }\n");
					}
					text.append("\n");
				}
				text.append("}\n");
				variants.add(text.toString());
			}

			Path dir = Files.createDirectories(tmp.resolve("round" + round));
			List<String> together = checkouts(dir.resolve("together"), variants);
			for (int v = 0; v < variants.size(); v++) {
				String alone = checkouts(dir.resolve("alone" + v), List.of(variants.get(v))).get(0);
				String committed = normalized(variants.get(v), true);
				if (!committed.equals(normalized(alone, true)))
					wrong.add("round " + round + ", variant " + v + " alone:\n--- committed ---\n" + variants.get(v) + "--- checked out ---\n" + alone);
				else if (!normalized(alone, false).equals(normalized(together.get(v), false)))
					wrong.add("round " + round + ", variant " + v + " of " + variants.size() + " together:\n--- committed ---\n" + variants.get(v)
							+ "--- checked out ---\n" + together.get(v));
			}
		}
		assertEquals(List.of(), wrong);
	}

	/** [variants] committed in turn into one repository, each under a feature of its own, then each checked out */
	private static List<String> checkouts(Path dir, List<String> variants) throws Exception {
		Path work = Files.createDirectories(dir.resolve("work"));
		List<String> result = new ArrayList<>();
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(dir.resolve(".ecco"));
			service.init();
			service.setBaseDir(work);
			Files.createDirectories(work.resolve(FILE).getParent());
			for (int v = 0; v < variants.size(); v++) {
				Files.writeString(work.resolve(FILE), variants.get(v));
				service.commit("variant " + v, "V" + v);
			}
			// the Java (AST) adapter read the file - with the text or file adapter the comparison proves little
			java.util.Set<String> plugins = new java.util.TreeSet<>();
			for (at.jku.isse.ecco.core.Association association : service.getRepository().getAssociations())
				((at.jku.isse.ecco.core.Association.Op) association).getRootNode().traverse((at.jku.isse.ecco.tree.Node.Op node) -> {
					if (node.getArtifact() != null && node.getArtifact().getData() instanceof at.jku.isse.ecco.adapter.dispatch.PluginArtifactData data)
						plugins.add(data.getPluginId());
				});
			assertEquals(java.util.Set.of(JavaASTPlugin.class.getName()), plugins);
			for (int v = 0; v < variants.size(); v++) {
				Path out = Files.createDirectories(dir.resolve("out" + v));
				service.setBaseDir(out);
				service.checkout("V" + v);
				result.add(Files.exists(out.resolve(FILE)) ? Files.readString(out.resolve(FILE)) : "(no file)");
			}
		}
		return result;
	}

	/** [source] parsed and printed, imports and each type's members sorted, with or without its comments - the content, not its layout or order */
	private static String normalized(String source, boolean comments) {
		CompilationUnit unit = PARSER.parse(source).getResult().orElseThrow(() -> new AssertionError("does not parse:\n" + source));
		if (!comments)
			for (Comment comment : unit.getAllContainedComments())
				comment.remove();
		unit.getImports().sort(Comparator.comparing(Node::toString));
		for (TypeDeclaration<?> type : unit.getTypes())
			type.getMembers().sort(Comparator.comparing(Node::toString));
		return unit.toString();
	}
}
