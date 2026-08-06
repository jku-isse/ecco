package at.jku.isse.ecco.genericAdapter.grammarInferencer.ruleInductionSequitur;



public class NonTerminal extends Symbol implements Cloneable {

	Rule r;

	NonTerminal(Rule theRule) {
		r = theRule;
		r.count++;
		value = NON_TERMINAL_CONST + r.number;
		p = null;
		n = null;
	}

	/**
	 * Extra cloning method necessary so that count in the corresponding rule is
	 * increased.
	 */

	protected Object clone() {

		NonTerminal sym = new NonTerminal(r);

		sym.p = p;
		sym.n = n;
		return sym;
	}

	public void cleanUp() {
		join(p, n);
		deleteDigram();
		r.count--;
	}

	public boolean isNonTerminal() {
		return true;
	}

	/**
	 * This symbol is the last reference to its rule. The contents of the rule
	 * are substituted in its place.
	 */

	public void expand() {
		join(p, r.first());
		join(r.last(), n);

		// Bug fix (21.8.2012): digram consisting of the last element of
		// the inserted rule and the first element after the inserted rule
		// must be put into the hash table (Simon Schwarzer)

		theDigrams.put(r.last(), r.last());

		// Necessary so that garbage collector
		// can delete rule and guard.

		r.theGuard.r = null;
		r.theGuard = null;
	}
}
