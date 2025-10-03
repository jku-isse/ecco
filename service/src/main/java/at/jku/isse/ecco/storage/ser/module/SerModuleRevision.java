package at.jku.isse.ecco.storage.ser.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;

import java.util.Collection;
import java.util.LinkedList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link ModuleRevision}.
 */
public class SerModuleRevision implements ModuleRevision {

	public static final long serialVersionUID = 1L;


	private FeatureRevision[] pos;
	private Feature[] neg;
	private int count;
	private Module module;


	public SerModuleRevision(SerModule module, FeatureRevision[] pos, Feature[] neg) {
		checkNotNull(module);
		checkNotNull(pos);
		checkNotNull(neg);
		checkArgument(pos.length > 0);
		this.verify(pos, neg);
		this.pos = pos;
		this.neg = neg;
		this.count = 0;
		this.module = module;
	}


	@Override
	public FeatureRevision[] getPos() {
		return this.pos;
	}

	@Override
	public Feature[] getNeg() {
		return this.neg;
	}

	@Override
	public int getCount() {
		return this.count;
	}

	@Override
	public void setCount(int count) {
		this.count = count;
	}

	@Override
	public void incCount() {
		this.count++;
	}

	@Override
	public void incCount(int count) {
		this.count += count;
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getConditionString() {
		// module-revision -> (all positive feature-revisions, no negative feature)
		//					  conjunction of all positive feature-revisions
		//					  and
		//					  not(disjunction of all negative features)

		FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();
		Collection<Formula> positiveFormulas = this.getFeatureRevisionFormulas();
		Formula positiveFormula = formulaFactory.and(positiveFormulas);
		Formula negativeFormula = this.getNegativeFormula();

		Formula condition = formulaFactory.and(positiveFormula, formulaFactory.not(negativeFormula));
		return condition.toString();
	}

	private Formula getNegativeFormula(){
		Collection<Formula> negativeFormulas = this.getFeatureFormulas();
		if (negativeFormulas.size() == 0){
			// negative formulas must not hold for the condition to hold
			// -> if there are none "false" must not hold, which is always the case
			return FormulaFactoryProvider.getFormulaFactory().constant(false);
		} else {
			return FormulaFactoryProvider.getFormulaFactory().or(negativeFormulas);
		}
	}

	private Collection<Formula> getFeatureRevisionFormulas(){
		Collection<Formula> formulas = new LinkedList<>();
		for (FeatureRevision featureRevision : this.pos){
			String conditionString = featureRevision.getLogicLiteralRepresentation();
			Formula condition = this.parseString(conditionString);
			formulas.add(condition);
		}
		return formulas;
	}

	private Collection<Formula> getFeatureFormulas(){
		Collection<Formula> formulas = new LinkedList<>();
		for (Feature feature : this.neg){
			String conditionString = feature.getLatestRevision().getLogicLiteralRepresentation();
			Formula condition = this.parseString(conditionString);
			formulas.add(condition);
		}
		return formulas;
	}

	private Formula parseString(String string){
		try{
			return FormulaFactoryProvider.getFormulaFactory().parse(string);
		} catch (ParserException e){
			throw new RuntimeException("Formula-String in module-revision could not be parsed: " + e.getMessage());
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SerModuleRevision serModuleRevision = (SerModuleRevision) o;

		//return Arrays.equals(pos, memModuleRevision.pos) && Arrays.equals(neg, memModuleRevision.neg);
		if (this.pos.length != serModuleRevision.pos.length || this.neg.length != serModuleRevision.neg.length)
			return false;
		for (int i = 0; i < this.pos.length; i++) {
			boolean found = false;
			for (int j = 0; j < serModuleRevision.pos.length; j++) {
				if (this.pos[i].equals(serModuleRevision.pos[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		for (int i = 0; i < this.neg.length; i++) {
			boolean found = false;
			for (int j = 0; j < serModuleRevision.neg.length; j++) {
				if (this.neg[i].equals(serModuleRevision.neg[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
//		int result = Arrays.hashCode(pos);
//		result = 31 * result + Arrays.hashCode(neg);
//		return result;
		int result = 0;
		for (FeatureRevision featureRevision : this.pos)
			result += featureRevision.hashCode();
		result *= 31;
		for (Feature feature : this.neg)
			result += feature.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return this.getModuleRevisionString();
	}

}
