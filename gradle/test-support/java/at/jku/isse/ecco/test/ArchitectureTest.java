package at.jku.isse.ecco.test;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The module's packages do not depend on each other in a circle, but for the circles known
 * ({@link #KNOWN_CYCLES}), and its sources import nothing they do not use. Part of every module's
 * tests (gradle/test-support); the build names the module's classes and sources
 * ({@code ecco.architecture.classes}, {@code ecco.architecture.sources}). Modules cannot depend on
 * each other in a circle - Gradle refuses it - so a circle is always within one module.
 */
public class ArchitectureTest {

	/**
	 * The circles kept, each its packages: a circle of these, or of some of them, is allowed - one
	 * with a package more, or of others, fails. The domain model's references both ways (a node and
	 * its artifact, an artifact and its order graph, a node and its association); the serialization
	 * storage's implementations of them; adapters whose viewer names its plugin
	 * ({@code getPluginId}), which binds it; LilyPond, whose token data are stored under their names.
	 */
	static final List<Set<String>> KNOWN_CYCLES = List.of(
			Set.of("at.jku.isse.ecco.artifact", "at.jku.isse.ecco.composition", "at.jku.isse.ecco.core", "at.jku.isse.ecco.counter",
					"at.jku.isse.ecco.dao", "at.jku.isse.ecco.featuretrace", "at.jku.isse.ecco.maintree.building", "at.jku.isse.ecco.pog",
					"at.jku.isse.ecco.repository", "at.jku.isse.ecco.tree"),
			Set.of("at.jku.isse.ecco.storage.ser.artifact", "at.jku.isse.ecco.storage.ser.pog"),
			Set.of("at.jku.isse.ecco.storage.common.dao", "at.jku.isse.ecco.storage.ser.core", "at.jku.isse.ecco.storage.ser.counter",
					"at.jku.isse.ecco.storage.ser.dao", "at.jku.isse.ecco.storage.ser.repository"),
			Set.of("at.jku.isse.ecco.adapter.c", "at.jku.isse.ecco.adapter.c.view"),
			Set.of("at.jku.isse.ecco.adapter.java", "at.jku.isse.ecco.adapter.java.view"),
			Set.of("at.jku.cdl.ecco.adapter.java", "at.jku.cdl.ecco.adapter.java.view"),
			Set.of("at.jku.isse.ecco.adapter.markdown", "at.jku.isse.ecco.adapter.markdown.view"),
			Set.of("at.jku.isse.ecco.adapter.lilypond", "at.jku.isse.ecco.adapter.lilypond.data", "at.jku.isse.ecco.adapter.lilypond.data.token",
					"at.jku.isse.ecco.adapter.lilypond.parser", "at.jku.isse.ecco.adapter.lilypond.view"));

	@Test
	public void packagesDoNotDependOnEachOtherInACircle() {
		String classes = System.getProperty("ecco.architecture.classes");
		if (classes == null || classes.isBlank())
			return; // not run by the build
		List<Path> dirs = Arrays.stream(classes.split(File.pathSeparator)).map(Path::of).filter(Files::isDirectory).toList();
		if (dirs.isEmpty())
			return;
		JavaClasses imported = new ClassFileImporter().importPaths(dirs);

		Map<String, Set<String>> dependencies = new TreeMap<>();
		Map<String, String> example = new HashMap<>();
		Set<String> packages = imported.stream().map(JavaClass::getPackageName).collect(Collectors.toCollection(TreeSet::new));
		for (JavaClass javaClass : imported) {
			String from = javaClass.getPackageName();
			for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
				String to = dependency.getTargetClass().getPackageName();
				if (!to.equals(from) && packages.contains(to)) {
					dependencies.computeIfAbsent(from, p -> new TreeSet<>()).add(to);
					example.putIfAbsent(from + " -> " + to, javaClass.getSimpleName() + " -> " + dependency.getTargetClass().getSimpleName());
				}
			}
		}

		List<String> unexpected = new ArrayList<>();
		for (Set<String> cycle : stronglyConnected(packages, dependencies))
			if (KNOWN_CYCLES.stream().noneMatch(known -> known.containsAll(cycle))) {
				StringBuilder text = new StringBuilder(cycle.toString());
				for (String from : cycle)
					for (String to : dependencies.getOrDefault(from, Set.of()))
						if (cycle.contains(to))
							text.append("\n    ").append(from).append(" -> ").append(to).append(" (").append(example.get(from + " -> " + to)).append(")");
				unexpected.add(text.toString());
			}
		assertEquals(List.of(), unexpected, "packages depending on each other in a circle - break it, or, if it must stay, add it to KNOWN_CYCLES");
	}

	@Test
	public void sourcesImportNothingTheyDoNotUse() throws IOException {
		String sources = System.getProperty("ecco.architecture.sources");
		if (sources == null || sources.isBlank())
			return; // not run by the build
		List<String> unused = new ArrayList<>();
		for (String dir : sources.split(File.pathSeparator)) {
			Path root = Path.of(dir);
			if (!Files.isDirectory(root) || root.toString().contains(File.separator + "build" + File.separator))
				continue;
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.toString().contains(File.separator + "generated" + File.separator)).toList()) {
					String source = Files.readString(file);
					if (source.startsWith("// Generated from")) // ANTLR's, written into src (Go adapter)
						continue;
					for (String name : unusedImports(source))
						unused.add(root.relativize(file) + ": " + name);
				}
			}
		}
		assertEquals(List.of(), unused, "imports not used");
	}

	private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?[\\w.]+\\.(\\w+)\\s*;[ \\t]*\\R", Pattern.MULTILINE);

	/** The single-type imports of [source] whose name its code and javadoc never mention. */
	static List<String> unusedImports(String source) {
		String code = code(IMPORT.matcher(source).replaceAll(""));
		List<String> unused = new ArrayList<>();
		Matcher imports = IMPORT.matcher(source);
		while (imports.find())
			if (!Pattern.compile("\\b" + imports.group(1) + "\\b").matcher(code).find())
				unused.add(imports.group().strip());
		return unused;
	}

	/** [source] with its comments, strings and characters blanked - javadoc kept, it names classes too. In one pass: "//" in a string is no comment. */
	static String code(String source) {
		StringBuilder code = new StringBuilder();
		int i = 0, n = source.length();
		while (i < n) {
			char c = source.charAt(i);
			if (source.startsWith("/**", i) && !source.startsWith("/**/", i)) {
				int end = source.indexOf("*/", i + 3);
				end = end < 0 ? n : end + 2;
				code.append(source, i, end);
				i = end;
			} else if (source.startsWith("/*", i)) {
				int end = source.indexOf("*/", i + 2);
				i = end < 0 ? n : end + 2;
				code.append(' ');
			} else if (source.startsWith("//", i)) {
				int end = source.indexOf('\n', i);
				i = end < 0 ? n : end;
			} else if (source.startsWith("\"\"\"", i)) {
				int end = source.indexOf("\"\"\"", i + 3);
				i = end < 0 ? n : end + 3;
				code.append("\"\"");
			} else if (c == '"' || c == '\'') {
				int j = i + 1;
				while (j < n && source.charAt(j) != c)
					j += source.charAt(j) == '\\' ? 2 : 1;
				i = j + 1;
				code.append("\"\"");
			} else {
				code.append(c);
				i++;
			}
		}
		return code.toString();
	}

	/** The circles of [packages] - strongly connected, more than one package each (Tarjan). */
	private static List<Set<String>> stronglyConnected(Set<String> packages, Map<String, Set<String>> dependencies) {
		Map<String, Integer> index = new HashMap<>();
		Map<String, Integer> low = new HashMap<>();
		List<String> stack = new ArrayList<>();
		Set<String> onStack = new TreeSet<>();
		List<Set<String>> components = new ArrayList<>();
		for (String start : packages)
			if (!index.containsKey(start))
				visit(start, dependencies, index, low, stack, onStack, components);
		return components;
	}

	private static void visit(String node, Map<String, Set<String>> dependencies, Map<String, Integer> index, Map<String, Integer> low,
							  List<String> stack, Set<String> onStack, List<Set<String>> components) {
		index.put(node, index.size());
		low.put(node, index.get(node));
		stack.add(node);
		onStack.add(node);
		for (String next : dependencies.getOrDefault(node, Set.of())) {
			if (!index.containsKey(next)) {
				visit(next, dependencies, index, low, stack, onStack, components);
				low.put(node, Math.min(low.get(node), low.get(next)));
			} else if (onStack.contains(next))
				low.put(node, Math.min(low.get(node), index.get(next)));
		}
		if (low.get(node).equals(index.get(node))) {
			Set<String> component = new TreeSet<>();
			String member;
			do {
				member = stack.removeLast();
				onStack.remove(member);
				component.add(member);
			} while (!member.equals(node));
			if (component.size() > 1)
				components.add(component);
		}
	}
}
