package at.jku.isse.ecco.genericAdapter.grammarInferencer.ruleInductionSequitur;


public class Guard extends Symbol {

	Rule r;

	Guard(Rule theRule) {
		r = theRule;
		value = "0";
		p = this;
		n = this;
	}

	public void cleanUp() {
		join(p, n);
	}

	public boolean isGuard() {
		return true;
	}

	public void deleteDigram() {

		// Do nothing
	}

	public boolean check() {
		return false;
	}
}
