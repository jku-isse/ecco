package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;
import org.logicng.solvers.SolverState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Drops redundant literals/terms from a presence condition, subject to a feature-model "care set"
 * (see {@link FeatureModelFormula}). Zero ECCO dependency beyond LogicNG: a presence condition here
 * is a plain sum-of-products ({@link Term}s ORed together, each an AND of positive/negative feature
 * name literals) -- deliberately mirroring the shape of {@code Module}/{@code Condition} in
 * {@code base} (pos/neg feature conjunctions, ORed across modules) so a future bridge can convert
 * mechanically between the two, without this class ever touching {@code base}/{@code service}
 * persistence types itself.
 *
 * <p>A literal or term is only dropped if the resulting formula is provably equivalent to the
 * original everywhere the feature model holds (checked via SAT, not assumed) -- outside the
 * feature model's valid configurations the two may disagree, which is fine, since those
 * configurations are never real by construction of the feature model itself. Reduction is greedy
 * (one pass, each candidate literal/term tried once) rather than an exhaustive search for the
 * globally smallest form.
 */
public final class PresenceConditionMinimizer {

    /** A conjunction of positive and negative feature-name literals (one product term of a DNF). */
    public static final class Term {
        public final Set<String> positive;
        public final Set<String> negative;

        public Term(Set<String> positive, Set<String> negative) {
            this.positive = Set.copyOf(positive);
            this.negative = Set.copyOf(negative);
        }

        @Override
        public String toString() {
            List<String> parts = new ArrayList<>();
            positive.stream().sorted().forEach(parts::add);
            negative.stream().sorted().forEach(n -> parts.add("!" + n));
            return parts.isEmpty() ? "TRUE" : String.join(" & ", parts);
        }
    }

    private PresenceConditionMinimizer() {
    }

    /** Renders a DNF as {@code "T1  OR  T2  OR  ..."} (or {@code "FALSE"} if empty). */
    public static String format(List<Term> terms) {
        if (terms.isEmpty()) return "FALSE";
        return terms.stream().map(Object::toString).collect(Collectors.joining("  OR  "));
    }

    /** Converts a DNF (OR of {@link Term}s) into a LogicNG {@link Formula}. */
    public static Formula toFormula(List<Term> terms) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        if (terms.isEmpty()) return f.falsum();
        List<Formula> termFormulas = new ArrayList<>();
        for (Term term : terms) termFormulas.add(termFormula(f, term));
        return f.or(termFormulas);
    }

    private static Formula termFormula(FormulaFactory f, Term term) {
        List<Formula> literals = new ArrayList<>();
        for (String name : term.positive) literals.add(f.literal(name, true));
        for (String name : term.negative) literals.add(f.literal(name, false));
        return literals.isEmpty() ? f.verum() : f.and(literals);
    }

    /**
     * Returns a DNF equivalent to {@code terms} everywhere {@code featureModel} holds, with
     * redundant literals and wholly-redundant terms greedily removed.
     *
     * <p>One {@link SATSolver} is created for the whole call, with {@code featureModel} added once
     * and every per-candidate check done via {@link SATSolver#saveState()}/{@code loadState} (a
     * push/pop, not a fresh solver) -- for an association with many terms this used to construct and
     * re-encode a brand new solver, including re-adding the entire (potentially large)
     * {@code featureModel}, for every single literal/term candidate; on a real repository with many
     * hard accepted constraints and associations with dozens of OR-terms, that repeated setup cost
     * was the actual bottleneck, not the SAT solving itself. Reusing one solver changes nothing about
     * what is checked or how the result is interpreted -- see {@link #isEquivalentUnderCareSet}.
     */
    public static List<Term> minimize(Formula featureModel, List<Term> terms) {
        List<Term> current = new ArrayList<>(terms);
        Formula original = toFormula(current);

        // syntactic absorption pre-pass (X + XY = X): unconditionally true, no SAT needed, and on
        // real associations with many overlapping OR-terms this shrinks both the term count and the
        // literal-removal search below before the expensive SAT loop ever runs.
        current = absorb(current);

        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        SATSolver solver = MiniSat.miniSat(f);
        solver.add(featureModel);

        // drop wholly-redundant terms first: cheaper, and shrinks the literal-removal search
        for (int i = current.size() - 1; i >= 0; i--) {
            List<Term> candidate = new ArrayList<>(current);
            candidate.remove(i);
            if (isEquivalentUnderCareSet(solver, original, toFormula(candidate))) {
                current = candidate;
            }
        }

        for (int i = 0; i < current.size(); i++) {
            for (String name : new LinkedHashSet<>(current.get(i).positive)) {
                current = tryReplace(solver, original, current, i, withoutPositive(current.get(i), name));
            }
            for (String name : new LinkedHashSet<>(current.get(i).negative)) {
                current = tryReplace(solver, original, current, i, withoutNegative(current.get(i), name));
            }
        }

        return current;
    }

    /**
     * Drops terms whose literals are a syntactic superset of some other term's -- the boolean
     * identity {@code X + XY = X}, which holds unconditionally (no feature model / SAT required).
     * Ties (two identical terms) keep only the earlier one.
     */
    static List<Term> absorb(List<Term> terms) {
        List<Term> result = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            Term ti = terms.get(i);
            boolean absorbed = false;
            for (int j = 0; j < terms.size() && !absorbed; j++) {
                if (i != j && subsumes(terms.get(j), ti, j, i)) {
                    absorbed = true;
                }
            }
            if (!absorbed) result.add(ti);
        }
        return result;
    }

    /** True if {@code smaller}'s literals make {@code larger} redundant via {@code X + XY = X}. */
    private static boolean subsumes(Term smaller, Term larger, int smallerIndex, int largerIndex) {
        if (!larger.positive.containsAll(smaller.positive) || !larger.negative.containsAll(smaller.negative)) {
            return false;
        }
        int smallerSize = smaller.positive.size() + smaller.negative.size();
        int largerSize = larger.positive.size() + larger.negative.size();
        if (smallerSize < largerSize) return true;
        return smallerSize == largerSize && smallerIndex < largerIndex; // identical terms: keep the earlier
    }

    private static List<Term> tryReplace(SATSolver solver, Formula original, List<Term> current, int index, Term shrunkTerm) {
        List<Term> candidate = new ArrayList<>(current);
        candidate.set(index, shrunkTerm);
        if (isEquivalentUnderCareSet(solver, original, toFormula(candidate))) {
            return candidate;
        }
        return current;
    }

    private static Term withoutPositive(Term term, String name) {
        Set<String> positive = new LinkedHashSet<>(term.positive);
        positive.remove(name);
        return new Term(positive, term.negative);
    }

    private static Term withoutNegative(Term term, String name) {
        Set<String> negative = new LinkedHashSet<>(term.negative);
        negative.remove(name);
        return new Term(term.positive, negative);
    }

    /**
     * True iff {@code featureModel} implies {@code a <-> b}, checked by SAT-refuting its negation --
     * {@code featureModel} is already in {@code solver} (added once by the caller); this only pushes
     * the per-candidate {@code NOT(a <-> b)} clause, solves, and pops it back off, leaving the solver
     * exactly as it was for the next candidate.
     */
    private static boolean isEquivalentUnderCareSet(SATSolver solver, Formula a, Formula b) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        SolverState state = solver.saveState();
        try {
            solver.add(f.not(f.equivalence(a, b)));
            return solver.sat() == Tristate.FALSE;
        } finally {
            solver.loadState(state);
        }
    }
}
