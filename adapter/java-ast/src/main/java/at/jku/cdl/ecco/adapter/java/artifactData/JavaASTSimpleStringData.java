package at.jku.cdl.ecco.adapter.java.artifactData;

public class JavaASTSimpleStringData extends JavaASTData {
	

	private final String data;

	public JavaASTSimpleStringData(String data) {
		this.data = unformattedString(data);
	}

	public String getData() {
		return data;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		return data;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((data == null) ? 0 : data.hashCode());
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
		JavaASTSimpleStringData other = (JavaASTSimpleStringData) obj;
		if (data == null) {
			if (other.data != null)
				return false;
		} else if (!data.equals(other.data))
			return false;
		return true;
	}

}
