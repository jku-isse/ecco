package at.jku.isse.ecco.logic;

import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.io.Serializable;
import java.util.Objects;

/**
 * A condition - a propositional formula over feature variables - as ECCO passes it around: by its
 * text in LogicNG syntax, its formula made on demand.
 * <p>
 * Not a LogicNG {@link Formula} itself: a formula belongs to the formula factory that made it, and a
 * factory must not be used by two threads at once - so every thread has its own
 * ({@link FormulaFactoryProvider}), and conditions are set on one thread (a commit) and evaluated on
 * others (a checkout, the views, a minimization). {@link #formula()} gives the formula in the current
 * thread's factory, parsed once per thread ({@link LogicUtils#parseString}). Immutable; equal when
 * the texts are.
 */
public final class LogicCondition implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Always holds. */
	public static final LogicCondition TRUE = new LogicCondition("$true");
	/** Never holds. */
	public static final LogicCondition FALSE = new LogicCondition("$false");

	private final String text;

	private LogicCondition(String text) {
		this.text = text;
	}

	/** The condition [text] writes in LogicNG syntax; null for null. */
	public static LogicCondition of(String text) {
		return text == null ? null : new LogicCondition(text);
	}

	/** The condition [formula] is; null for null. */
	public static LogicCondition of(Formula formula) {
		return formula == null ? null : new LogicCondition(formula.toString());
	}

	/** This condition's formula, in the current thread's formula factory. */
	public Formula formula() {
		return LogicUtils.parseString(this.text);
	}

	/** Whether the condition holds for [assignment] - a variable it does not set is false. */
	public boolean evaluate(Assignment assignment) {
		return this.formula().evaluate(assignment);
	}

	/** The disjunction of [a] and [b]; either may be null (absent), then the other. */
	public static LogicCondition or(LogicCondition a, LogicCondition b) {
		if (a == null || a.equals(b))
			return b;
		if (b == null)
			return a;
		FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
		return of(f.or(a.formula(), b.formula()));
	}

	/** The conjunction of [a] and [b]; either may be null (absent), then the other. */
	public static LogicCondition and(LogicCondition a, LogicCondition b) {
		if (a == null || a.equals(b))
			return b;
		if (b == null)
			return a;
		FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
		return of(f.and(a.formula(), b.formula()));
	}

	/** The text in LogicNG syntax - what is stored. */
	public String text() {
		return this.text;
	}

	@Override
	public String toString() {
		return this.text;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof LogicCondition other && this.text.equals(other.text);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text);
	}
}
