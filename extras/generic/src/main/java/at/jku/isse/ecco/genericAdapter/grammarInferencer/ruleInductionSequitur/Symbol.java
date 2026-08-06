package at.jku.isse.ecco.genericAdapter.grammarInferencer.ruleInductionSequitur;

import java.util.HashMap;

public abstract class Symbol {

	public static final String NON_TERMINAL_CONST = "NONTERMINAL";

	static final int prime = 2265539;
	public static HashMap<Symbol, Symbol> theDigrams = new HashMap<>(Symbol.prime);

	public String value;
	public Symbol p;

	Symbol n;

	/**
	 * Links two symbols together, removing any old digram from the hash table.
	 */

	public static void join(Symbol left, Symbol right) {

		if (left.n != null) {
			left.deleteDigram();

			// Bug fix (21.8.2012): included two if statements, adapted from
			// sequitur_simple.cc, to deal with triples

			if ((right.p != null) && (right.n != null) && right.value == right.p.value && right.value == right.n.value) {
				theDigrams.put(right, right);
			}

			if ((left.p != null) && (left.n != null) && left.value == left.n.value && left.value == left.p.value) {
				theDigrams.put(left.p, left.p);
			}
		}

		left.n = right;
		right.p = left;
	}

	/**
	 * Abstract method: cleans up for symbol deletion.
	 */

	public abstract void cleanUp();

	/**
	 * Inserts a symbol after this one.
	 */

	public void insertAfter(Symbol toInsert) {
		join(toInsert, n);
		join(this, toInsert);
	}

	/**
	 * Removes the digram from the hash table. Overwritten in sub class guard.
	 */

	public void deleteDigram() {

		Symbol dummy;

		if (n.isGuard())
			return;
		dummy = (Symbol) theDigrams.get(this);

		// Only delete digram if its exactly
		// the stored one.

		if (dummy == this)
			theDigrams.remove(this);
	}

	/**
	 * Returns true if this is the guard symbol. Overwritten in subclass guard.
	 */

	public boolean isGuard() {
		return false;
	}

	/**
	 * Returns true if this is a non-terminal. Overwritten in subclass
	 * nonTerminal.
	 */

	public boolean isNonTerminal() {
		return false;
	}

	/**
	 * Checks a new digram. If it appears elsewhere, deals with it by calling
	 * match(), otherwise inserts it into the hash table. Overwritten in
	 * subclass guard.
	 */

	public boolean check() {

		Symbol found;

		if (n.isGuard())
			return false;
		if (!theDigrams.containsKey(this)) {
			found = (Symbol) theDigrams.put(this, this);
			return false;
		}
		found = (Symbol) theDigrams.get(this);
		if (found.n != this)
			match(this, found);
		return true;
	}

	/**
	 * Replace a digram with a non-terminal.
	 */

	public void substitute(Rule r) {
		cleanUp();
		n.cleanUp();
		p.insertAfter(new NonTerminal(r));
		if (!p.check())
			p.n.check();
	}

	/**
	 * Deal with a matching digram.
	 */

	public void match(Symbol newD, Symbol matching) {

		Rule r;
		Symbol first, second, dummy;

		if (matching.p.isGuard() && matching.n.n.isGuard()) {

			// reuse an existing rule

			r = ((Guard) matching.p).r;
			newD.substitute(r);
		} else {

			// create a new rule

			r = new Rule();
			try {
				first = (Symbol) newD.clone();
				second = (Symbol) newD.n.clone();
				r.theGuard.n = first;
				first.p = r.theGuard;
				first.n = second;
				second.p = first;
				second.n = r.theGuard;
				r.theGuard.p = second;

				matching.substitute(r);
				newD.substitute(r);

				// Bug fix (21.8.2012): moved the following line
				// to occur after substitutions (see sequitur_simple.cc)

				theDigrams.put(first, first);
			} catch (CloneNotSupportedException c) {
				c.printStackTrace();
			}
		}

		// Check for an underused rule.

		if (r.first().isNonTerminal() && (((NonTerminal) r.first()).r.count == 1))
			((NonTerminal) r.first()).expand();
	}

	/**
	 * Produce the hashcode for a digram.
	 */

	public int hashCode() {

		long code;

		// Values in linear combination with two
		// prime numbers.

		code = ((21599 * value.hashCode()) + (20507 * n.value.hashCode()));
		code = code % (long) prime;
		int intCode = (int) code;
		return (int) code;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Symbol) {
			Symbol other = (Symbol) obj;
			return other.value.equals(value) && other.n.value.equals(n.value);
		}
		return false;
	}
}
