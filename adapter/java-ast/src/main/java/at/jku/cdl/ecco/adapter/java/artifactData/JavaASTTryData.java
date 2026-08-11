package at.jku.cdl.ecco.adapter.java.artifactData;

import java.util.ArrayList;
import java.util.List;

public class JavaASTTryData extends JavaASTData {

	private List<String> expressions;
	private List<String> catchParams;
	private boolean isFinally;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public JavaASTTryData() {
		this.expressions = new ArrayList<>();
		this.catchParams = new ArrayList<>();
		this.setType(ASTNodeType.TRYBLOCK);
	}

	public void addExpression(String expression) {
		if (expression != null && expression.length() >= 1) {
			expressions.add(expression);
		}
	}

	public void addCatchParam(String param) {
		if (param != null && param.length() >= 1) {
			catchParams.add(param);
		}
	}

	public void setFinally(boolean value) {
		this.isFinally = value;
	}

	public List<String> getExpressions() {
		return expressions;
	}

	public List<String> getCatchParams() {
		return catchParams;
	}

	public boolean hasFinally() {
		return isFinally;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((catchParams == null) ? 0 : catchParams.hashCode());
		result = prime * result + ((expressions == null) ? 0 : expressions.hashCode());
		result = prime * result + (isFinally ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JavaASTTryData other = (JavaASTTryData) obj;
		if (catchParams == null) {
			if (other.catchParams != null)
				return false;
		} else if (!catchParams.equals(other.catchParams))
			return false;
		if (expressions == null) {
			if (other.expressions != null)
				return false;
		} else if (!expressions.equals(other.expressions))
			return false;
		if (isFinally != other.isFinally)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TRY [expressions=" + expressions + ", catchParams=" + catchParams + ", isFinally="
				+ isFinally + "]";
	}

}
