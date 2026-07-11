package at.jku.isse.ecco.mining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Mines candidate feature-model constraints (requires / excludes / mandatory)
 * from a sample of observed configurations.
 *
 * <p>This class is intentionally free of any ECCO dependency: it operates on
 * plain {@code Set<String>} feature tokens so it is trivially unit-testable and
 * correct regardless of ECCO's internal API. A thin bridge is responsible for
 * turning ECCO commits/variants into {@code List<Set<String>>}.
 *
 * <p><b>Epistemic contract:</b> every suggestion is a HYPOTHESIS induced from an
 * incomplete sample. "Never observed together" is not "forbidden" -- it may just
 * be untested. Callers must treat output as suggestions to be confirmed by a
 * human (or by additional commits), never as constraints to auto-apply.
 */
public final class ConstraintMiner {

    public enum Kind { REQUIRES, EXCLUDES, MANDATORY }

    /** A single ranked constraint hypothesis. */
    public static final class Suggestion {
        public final Kind kind;
        public final String a;                 // antecedent / first feature / mandatory feature
        public final String b;                 // consequent / second feature; null for MANDATORY
        public final double support;           // fraction of all configs the rule touches
        public final double confidence;        // 1.0 for a hard (exception-free) rule
        public final int witness;              // strength: #chances the rule had to be violated
        public final List<Integer> counterExamples; // config indices violating a near-miss; empty if hard

        Suggestion(Kind kind, String a, String b, double support, double confidence,
                   int witness, List<Integer> counterExamples) {
            this.kind = kind;
            this.a = a;
            this.b = b;
            this.support = support;
            this.confidence = confidence;
            this.witness = witness;
            this.counterExamples = List.copyOf(counterExamples);
        }

        /** True if no observed configuration violates this rule. */
        public boolean isHard() {
            return counterExamples.isEmpty();
        }

        @Override
        public String toString() {
            switch (kind) {
                case REQUIRES:
                    return String.format("REQUIRES  %s -> %s   (conf=%.3f, witness=%d, support=%.3f%s)",
                            a, b, confidence, witness, support,
                            counterExamples.isEmpty() ? "" : ", violations=" + counterExamples.size());
                case EXCLUDES:
                    return String.format("EXCLUDES  %s / %s   (witness=%d, support=%.3f%s)",
                            a, b, witness, support,
                            counterExamples.isEmpty() ? "" : ", violations=" + counterExamples.size());
                default:
                    return String.format("MANDATORY %s   (witness=%d)", a, witness);
            }
        }
    }

    private final int minWitness;             // t: don't propose a rule with fewer witnesses than this
    private final double nearMissConfidence;  // e.g. 0.90; set to 1.0 to emit ONLY exception-free rules
    private final Function<String, String> groupKey; // tokens sharing a group are alternatives -> pairs skipped

    /**
     * @param minWitness         minimum number of independent witnesses before a rule is proposed (>= 1)
     * @param nearMissConfidence confidence in [0,1]; 1.0 disables near-misses (hard rules only)
     * @param groupKey           maps a token to its group; pairs within the same group are skipped
     *                           (use this to stop different revisions of the SAME feature from being
     *                           reported as mutually exclusive). May be null (each token its own group).
     */
    public ConstraintMiner(int minWitness, double nearMissConfidence, Function<String, String> groupKey) {
        if (minWitness < 1) throw new IllegalArgumentException("minWitness must be >= 1");
        if (nearMissConfidence < 0.0 || nearMissConfidence > 1.0)
            throw new IllegalArgumentException("nearMissConfidence must be in [0,1]");
        this.minWitness = minWitness;
        this.nearMissConfidence = nearMissConfidence;
        this.groupKey = (groupKey != null) ? groupKey : Function.identity();
    }

    /** Convenience: hard rules only, no grouping. */
    public ConstraintMiner(int minWitness) {
        this(minWitness, 1.0, null);
    }

    /**
     * Mine constraints from the given configurations.
     *
     * @param configs each element is the set of feature tokens present in one committed variant
     * @return suggestions sorted: hard rules first, then by descending witness strength
     */
    public List<Suggestion> mine(List<? extends Set<String>> configs) {
        int m = configs.size();
        if (m == 0) return List.of();

        // ---- single-feature and pairwise co-occurrence counts (one pass) ----
        Map<String, Integer> single = new HashMap<>();
        Map<String, Map<String, Integer>> co = new HashMap<>(); // stored canonically for x < y

        for (Set<String> cfg : configs) {
            for (String f : cfg) single.merge(f, 1, Integer::sum);
            List<String> sorted = new ArrayList<>(cfg);
            Collections.sort(sorted);
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    co.computeIfAbsent(sorted.get(i), k -> new HashMap<>())
                            .merge(sorted.get(j), 1, Integer::sum);
                }
            }
        }

        List<Suggestion> out = new ArrayList<>();

        // ---- MANDATORY: present in every configuration ----
        for (Map.Entry<String, Integer> e : single.entrySet()) {
            if (e.getValue() == m) {
                out.add(new Suggestion(Kind.MANDATORY, e.getKey(), null, 1.0, 1.0, m, List.of()));
            }
        }

        // ---- REQUIRES: iterate observed co-occurrences (positive-confidence candidates only) ----
        for (Map.Entry<String, Map<String, Integer>> ex : co.entrySet()) {
            String x = ex.getKey();
            for (Map.Entry<String, Integer> ey : ex.getValue().entrySet()) {
                String y = ey.getKey();
                int c = ey.getValue();
                if (sameGroup(x, y)) continue;
                tryRequires(x, y, c, single, m, configs, out); // x -> y
                tryRequires(y, x, c, single, m, configs, out); // y -> x
            }
        }

        // ---- EXCLUDES: pairs of well-attested features that (almost) never co-occur ----
        List<String> attested = new ArrayList<>();
        for (Map.Entry<String, Integer> e : single.entrySet()) {
            if (e.getValue() >= minWitness && e.getValue() != m) attested.add(e.getKey());
        }
        Collections.sort(attested);
        for (int i = 0; i < attested.size(); i++) {
            for (int j = i + 1; j < attested.size(); j++) {
                String x = attested.get(i), y = attested.get(j);
                if (sameGroup(x, y)) continue;
                int c = coCount(co, x, y);
                int minCount = Math.min(single.get(x), single.get(y));
                double violationRatio = (double) c / minCount;
                if (c == 0) {
                    out.add(new Suggestion(Kind.EXCLUDES, x, y,
                            (double) minCount / m, 1.0, minCount, List.of()));
                } else if (violationRatio <= (1.0 - nearMissConfidence)) {
                    out.add(new Suggestion(Kind.EXCLUDES, x, y,
                            (double) minCount / m, 1.0 - violationRatio, minCount,
                            indicesContainingBoth(configs, x, y)));
                }
            }
        }

        out.sort(Comparator
                .comparingInt((Suggestion s) -> s.isHard() ? 0 : 1)
                .thenComparingInt(s -> -s.witness)
                .thenComparing(s -> s.kind.name())
                .thenComparing(s -> s.a)
                .thenComparing(s -> s.b == null ? "" : s.b));
        return out;
    }

    // A -> B, where c = co-occurrence(A,B). Skips degenerate rules where A or B is mandatory.
    private void tryRequires(String a, String b, int c, Map<String, Integer> single, int m,
                             List<? extends Set<String>> configs, List<Suggestion> out) {
        int na = single.get(a);
        int nb = single.get(b);
        if (na < minWitness) return;
        if (na == m || nb == m) return; // antecedent/consequent present everywhere -> not informative
        double confidence = (double) c / na;
        if (confidence == 1.0) {
            out.add(new Suggestion(Kind.REQUIRES, a, b, (double) c / m, 1.0, na, List.of()));
        } else if (confidence >= nearMissConfidence) {
            out.add(new Suggestion(Kind.REQUIRES, a, b, (double) c / m, confidence, na,
                    indicesContainingButNot(configs, a, b)));
        }
    }

    private boolean sameGroup(String x, String y) {
        return groupKey.apply(x).equals(groupKey.apply(y));
    }

    private static int coCount(Map<String, Map<String, Integer>> co, String x, String y) {
        String lo = (x.compareTo(y) < 0) ? x : y;
        String hi = (x.compareTo(y) < 0) ? y : x;
        Map<String, Integer> row = co.get(lo);
        if (row == null) return 0;
        return row.getOrDefault(hi, 0);
    }

    private static List<Integer> indicesContainingButNot(List<? extends Set<String>> configs, String a, String b) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            Set<String> cfg = configs.get(i);
            if (cfg.contains(a) && !cfg.contains(b)) idx.add(i);
        }
        return idx;
    }

    private static List<Integer> indicesContainingBoth(List<? extends Set<String>> configs, String a, String b) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            Set<String> cfg = configs.get(i);
            if (cfg.contains(a) && cfg.contains(b)) idx.add(i);
        }
        return idx;
    }
}
