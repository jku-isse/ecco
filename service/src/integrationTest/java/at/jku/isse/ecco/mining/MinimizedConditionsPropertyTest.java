package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.service.EccoService;
import net.jqwik.api.*;
import net.jqwik.api.statistics.Statistics;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR-N9b on generated product lines: checking out with minimized conditions writes the same files as
 * checking out without them, for every configuration the accepted constraints allow - not only the
 * committed ones. A product line is a hidden feature model over A-D (requires, excludes, mandatory)
 * and the lines of main.txt with presence conditions over them, plus a file of each feature's own;
 * the variants committed are configurations the hidden model allows (BASE in each), some more than
 * once. The hard constraints mined from them are accepted, the conditions minimized under them
 * (FR-N7a), and every configuration of BASE and A-D that violates none of them is checked out both
 * ways. MinimizedCheckoutEquivalenceTest checks one product line by hand; this is the same claim for
 * every product line jqwik generates, and a failing one is shrunk to a smallest one.
 */
class MinimizedConditionsPropertyTest {

	private static final List<String> FEATURES = List.of("A", "B", "C", "D");
	private static final String BASE = "BASE";

	// ---------------------------------------------------------------------------------------------
	// The product line

	/** A requires, excludes or mandatory rule over feature names, as the miner proposes them. */
	record Rule(Constraint.Kind kind, String a, String b) {
		boolean allows(Set<String> configuration) {
			return !kind.isViolatedBy(configuration, a, b);
		}

		@Override
		public String toString() {
			return kind == Constraint.Kind.MANDATORY ? "MANDATORY " + a : kind + " " + a + " " + b;
		}
	}

	/** A presence condition: always, x, x and y, x and not y, x or y. */
	record Condition(String kind, String x, String y) {
		boolean holds(Set<String> configuration) {
			return switch (kind) {
				case "true" -> true;
				case "x" -> configuration.contains(x);
				case "and" -> configuration.contains(x) && configuration.contains(y);
				case "andNot" -> configuration.contains(x) && !configuration.contains(y);
				case "or" -> configuration.contains(x) || configuration.contains(y);
				default -> throw new IllegalStateException(kind);
			};
		}

		@Override
		public String toString() {
			return switch (kind) {
				case "true" -> "true";
				case "x" -> x;
				case "and" -> x + "&" + y;
				case "andNot" -> x + "&!" + y;
				default -> x + "|" + y;
			};
		}
	}

	/** The hidden feature model, main.txt's lines' conditions, and the variants committed in turn. */
	record ProductLine(List<Rule> model, List<Condition> lines, List<Set<String>> variants) {
		@Override
		public String toString() {
			return "\n  model " + model + "\n  lines " + lines + "\n  variants " + variants.stream()
					.map(v -> String.join(",", new TreeSet<>(v))).collect(Collectors.joining(" | "));
		}
	}

	/** Every set of BASE and A-D (BASE optional, as a feature model sees it) that [rules] allow, but the empty one. */
	static List<Set<String>> allowed(Collection<Rule> rules, List<String> features) {
		List<String> all = new ArrayList<>(features);
		all.add(0, BASE);
		List<Set<String>> allowed = new ArrayList<>();
		for (int mask = 1; mask < 1 << all.size(); mask++) {
			Set<String> configuration = new TreeSet<>();
			for (int f = 0; f < all.size(); f++)
				if ((mask & 1 << f) != 0)
					configuration.add(all.get(f));
			if (rules.stream().allMatch(rule -> rule.allows(configuration)))
				allowed.add(configuration);
		}
		return allowed;
	}

	// ---------------------------------------------------------------------------------------------
	// Generators

	private Arbitrary<Rule> rules() {
		Arbitrary<String> feature = Arbitraries.of(FEATURES);
		return Arbitraries.frequencyOf(
				Tuple.of(3, Combinators.combine(feature, feature).filter((a, b) -> !a.equals(b)).as((a, b) -> new Rule(Constraint.Kind.REQUIRES, a, b))),
				Tuple.of(3, Combinators.combine(feature, feature).filter((a, b) -> !a.equals(b)).as((a, b) -> new Rule(Constraint.Kind.EXCLUDES, a, b))),
				Tuple.of(1, feature.map(a -> new Rule(Constraint.Kind.MANDATORY, a, null))));
	}

	private Arbitrary<Condition> conditions() {
		Arbitrary<String> feature = Arbitraries.of(FEATURES);
		return Arbitraries.frequencyOf(
				Tuple.of(1, Arbitraries.just(new Condition("true", null, null))),
				Tuple.of(3, feature.map(x -> new Condition("x", x, null))),
				Tuple.of(2, Combinators.combine(feature, feature).filter((x, y) -> !x.equals(y)).as((x, y) -> new Condition("and", x, y))),
				Tuple.of(2, Combinators.combine(feature, feature).filter((x, y) -> !x.equals(y)).as((x, y) -> new Condition("andNot", x, y))),
				Tuple.of(2, Combinators.combine(feature, feature).filter((x, y) -> !x.equals(y)).as((x, y) -> new Condition("or", x, y))));
	}

	@Provide
	Arbitrary<ProductLine> productLines() {
		return rules().list().ofMaxSize(3).flatMap(model -> {
			// the hidden model's configurations with BASE - the variants of this product line
			List<Set<String>> variants = allowed(model, FEATURES).stream().filter(c -> c.contains(BASE)).toList();
			if (variants.isEmpty())
				return Arbitraries.just(null);
			return Combinators.combine(conditions().list().ofMinSize(2).ofMaxSize(8), Arbitraries.of(variants).list().ofMinSize(3).ofMaxSize(10))
					.as((lines, committed) -> new ProductLine(model, lines, committed));
		}).filter(Objects::nonNull);
	}

	// ---------------------------------------------------------------------------------------------
	// The implementation

	/** main.txt with the lines whose condition holds, between two lines every variant has, and a file per feature */
	static void writeVariant(Path dir, ProductLine productLine, Set<String> configuration) throws IOException {
		FileUtils.cleanDirectory(dir.toFile());
		List<String> main = new ArrayList<>(List.of("start"));
		for (int i = 0; i < productLine.lines().size(); i++)
			if (productLine.lines().get(i).holds(configuration))
				main.add("line" + i + " " + productLine.lines().get(i));
		main.add("end");
		Files.write(dir.resolve("main.txt"), main);
		for (String feature : configuration)
			if (!feature.equals(BASE))
				Files.write(dir.resolve(feature.toLowerCase() + ".txt"), List.of(feature + " only"));
	}

	static Map<String, String> checkout(EccoService service, Path dir, String configuration) throws IOException {
		Files.createDirectories(dir);
		service.setBaseDir(dir);
		service.checkout(configuration);
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(dir)) {
			// .hashes records absolute paths and the time, so it differs between any two checkouts
			for (Path p : paths.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals(".hashes")).toList())
				files.put(dir.relativize(p).toString(), Files.readString(p));
		}
		return files;
	}

	// ---------------------------------------------------------------------------------------------
	// The property

	@Property(tries = 500)
	void minimizedCheckoutsWriteTheSameFilesForEveryAllowedConfiguration(@ForAll("productLines") ProductLine productLine) throws Exception {
		Path tmp = Files.createTempDirectory("minimized-property");
		try (EccoService service = new EccoService()) {
			Path work = Files.createDirectories(tmp.resolve("work"));
			service.setRepositoryDir(tmp.resolve(".ecco"));
			service.init();
			service.setBaseDir(work);
			for (Set<String> variant : productLine.variants()) {
				writeVariant(work, productLine, variant);
				String configuration = String.join(", ", new TreeSet<>(variant));
				service.commit(configuration, configuration);
			}

			// the hard constraints the committed variants show, accepted - the feature model minimization uses
			List<ConstraintMiner.Suggestion> hard = new ConstraintMiner(2, 1.0, null)
					.mine(ConfigurationBridge.readConfigurations(service.getCommits())).stream()
					.filter(ConstraintMiner.Suggestion::isHard).toList();
			if (!hard.isEmpty())
				service.acceptConstraints(hard);
			List<Rule> accepted = hard.stream().map(s -> new Rule(s.kind, s.a, s.b)).toList();

			int associations = service.getRepository().getAssociations().size();
			assertEquals(associations, service.minimizeConditionsForCheckout(), "every association minimized");
			Map<String, LogicCondition> valid = service.validCheckoutConditions();
			assertEquals(associations, valid.size(), "every minimized condition valid right after minimizing");
			long simpler = service.getRepository().getAssociations().stream()
					.filter(a -> valid.get(a.getId()).text().length() < ((Association) a).computeCondition().toLogicString().length()).count();
			Statistics.label("conditions minimized to something shorter").collect(simpler == 0 ? "none" : simpler == associations ? "all" : "some");

			// every configuration the accepted constraints allow, of the features the repository has
			Set<String> known = service.getRepository().getFeatures().stream().map(f -> f.getName()).collect(Collectors.toSet());
			List<String> features = FEATURES.stream().filter(known::contains).toList();
			List<Set<String>> allowed = allowed(accepted, features).stream().filter(c -> known.containsAll(c)).toList();
			Set<Set<String>> committed = productLine.variants().stream().map(TreeSet::new).collect(Collectors.toSet());
			long uncommitted = allowed.stream().filter(c -> !committed.contains(new TreeSet<>(c))).count();
			Statistics.label("allowed configurations never committed").collect(uncommitted == 0 ? "0" : uncommitted <= 4 ? "1-4" : "5+");

			int i = 0;
			for (Set<String> configuration : allowed) {
				String text = String.join(", ", configuration);
				service.setMinimizedConditionsInCheckout(false);
				Map<String, String> normal = checkout(service, tmp.resolve("normal-" + i), text);
				service.setMinimizedConditionsInCheckout(true);
				Map<String, String> minimized = checkout(service, tmp.resolve("minimized-" + i), text);
				assertEquals(normal, minimized, "[" + text + "] under " + accepted);
				i++;
			}
		} finally {
			FileUtils.deleteDirectory(tmp.toFile());
		}
	}
}
