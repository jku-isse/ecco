package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import net.jqwik.api.*;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Variable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The constraint miner (FR-N1a-d, FR-N3) against a brute-force reference that counts over the
 * configurations directly, on generated sets of configurations: the suggestions are exactly those the
 * rules define - kinds, features, witnesses, confidences and counter-examples - hard ones first, then
 * by witnesses; no configuration violates a hard one (what minimization relies on, FR-N7a); and the
 * feature model compiled from them (FeatureModelFormula) holds exactly where no hard one is violated.
 */
class ConstraintMinerPropertyTest {

	/** tokens: features A-E, and two revisions of F grouped by the group key (FR-N3's alternatives) */
	private static final List<String> TOKENS = List.of("A", "B", "C", "D", "E", "F1", "F2");

	record Input(List<Set<String>> configs, int minWitness, double confidence, boolean grouped) {
		Function<String, String> groupKey() {
			return grouped ? token -> token.startsWith("F") ? "F" : token : null;
		}
	}

	@Provide
	Arbitrary<Input> inputs() {
		Arbitrary<Set<String>> config = Arbitraries.of(TOKENS).set().ofMaxSize(TOKENS.size());
		return Combinators.combine(config.list().ofMinSize(1).ofMaxSize(12), Arbitraries.integers().between(1, 5),
						Arbitraries.of(1.0, 0.95, 0.9, 0.75, 0.5, 0.0), Arbitraries.of(true, false))
				.as(Input::new);
	}

	/** What a suggestion says, comparable: kind, features, witness, confidence and counter-examples. */
	record Rule(Constraint.Kind kind, String a, String b, int witness, double confidence, List<Integer> counterExamples) {
		static Rule of(ConstraintMiner.Suggestion s) {
			return new Rule(s.kind, s.a, s.b, s.witness, s.confidence, s.counterExamples);
		}
	}

	/** The rules by their definition, counted over [input]'s configurations one by one. */
	static Set<Rule> reference(Input input) {
		List<Set<String>> configs = input.configs();
		int m = configs.size();
		Function<String, String> group = input.groupKey() == null ? Function.identity() : input.groupKey();
		Set<String> tokens = configs.stream().flatMap(Set::stream).collect(Collectors.toCollection(TreeSet::new));
		Map<String, Long> count = tokens.stream().collect(Collectors.toMap(t -> t, t -> configs.stream().filter(c -> c.contains(t)).count()));
		Set<Rule> rules = new HashSet<>();
		for (String t : tokens)
			if (count.get(t) == m)
				rules.add(new Rule(Constraint.Kind.MANDATORY, t, null, m, 1.0, List.of()));
		for (String a : tokens)
			for (String b : tokens) {
				if (a.equals(b) || group.apply(a).equals(group.apply(b)))
					continue;
				long na = count.get(a), nb = count.get(b);
				List<Integer> aWithoutB = IntStream.range(0, m).filter(i -> configs.get(i).contains(a) && !configs.get(i).contains(b)).boxed().toList();
				long both = na - aWithoutB.size();
				// REQUIRES a -> b: a well attested, neither everywhere, b with a at least once
				if (na >= input.minWitness() && na != m && nb != m && both > 0) {
					double confidence = (double) both / na;
					if (confidence == 1.0)
						rules.add(new Rule(Constraint.Kind.REQUIRES, a, b, (int) na, 1.0, List.of()));
					else if (confidence >= input.confidence())
						rules.add(new Rule(Constraint.Kind.REQUIRES, a, b, (int) na, confidence, aWithoutB));
				}
				// EXCLUDES a / b, once per pair: both well attested, neither everywhere
				if (a.compareTo(b) < 0 && na >= input.minWitness() && nb >= input.minWitness() && na != m && nb != m) {
					long min = Math.min(na, nb);
					List<Integer> withBoth = IntStream.range(0, m).filter(i -> configs.get(i).contains(a) && configs.get(i).contains(b)).boxed().toList();
					double ratio = (double) withBoth.size() / min;
					if (withBoth.isEmpty())
						rules.add(new Rule(Constraint.Kind.EXCLUDES, a, b, (int) min, 1.0, List.of()));
					else if (ratio <= 1.0 - input.confidence())
						rules.add(new Rule(Constraint.Kind.EXCLUDES, a, b, (int) min, 1.0 - ratio, withBoth));
				}
			}
		return rules;
	}

	static List<ConstraintMiner.Suggestion> mine(Input input) {
		return new ConstraintMiner(input.minWitness(), input.confidence(), input.groupKey()).mine(input.configs());
	}

	@Property(tries = 2000)
	void theMinerProposesExactlyTheRulesTheConfigurationsShow(@ForAll("inputs") Input input) {
		List<ConstraintMiner.Suggestion> mined = mine(input);
		List<Rule> rules = mined.stream().map(Rule::of).toList();
		assertEquals(rules.size(), new HashSet<>(rules).size(), "a rule proposed twice: " + mined);
		assertEquals(reference(input), new HashSet<>(rules));
	}

	/** FR-N1c: hard rules first, then by descending witnesses. */
	@Property(tries = 2000)
	void hardRulesComeFirstThenTheBestAttested(@ForAll("inputs") Input input) {
		List<ConstraintMiner.Suggestion> mined = mine(input);
		for (int i = 1; i < mined.size(); i++) {
			ConstraintMiner.Suggestion before = mined.get(i - 1), after = mined.get(i);
			assertFalse(!before.isHard() && after.isHard(), "a near-miss before a hard rule: " + mined);
			if (before.isHard() == after.isHard())
				assertTrue(before.witness >= after.witness, "fewer witnesses first: " + mined);
		}
	}

	/** What minimization relies on: no committed configuration violates a hard rule. */
	@Property(tries = 2000)
	void noConfigurationViolatesAHardRule(@ForAll("inputs") Input input) {
		for (ConstraintMiner.Suggestion rule : mine(input))
			if (rule.isHard())
				for (Set<String> config : input.configs())
					assertFalse(rule.kind.isViolatedBy(config, rule.a, rule.b), rule + " violated by " + config);
	}

	/** The feature model minimization uses holds for a selection exactly where no hard rule is violated. */
	@Property(tries = 2000)
	void theCompiledFeatureModelHoldsWhereNoHardRuleIsViolated(@ForAll("inputs") Input input, @ForAll("selections") Set<String> selection) {
		List<ConstraintMiner.Suggestion> mined = mine(input);
		Formula model = FeatureModelFormula.compile(mined);
		FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
		Assignment assignment = new Assignment();
		for (String token : TOKENS)
			assignment.addLiteral(f.literal(token, selection.contains(token)));
		for (Variable variable : model.variables())
			if (!TOKENS.contains(variable.name()))
				fail("a variable that is no feature: " + variable);
		boolean violated = mined.stream().filter(ConstraintMiner.Suggestion::isHard).anyMatch(rule -> rule.kind.isViolatedBy(selection, rule.a, rule.b));
		assertEquals(!violated, model.evaluate(assignment), "selection " + selection + " under " + mined);
	}

	@Provide
	Arbitrary<Set<String>> selections() {
		return Arbitraries.of(TOKENS).set().ofMaxSize(TOKENS.size());
	}
}
