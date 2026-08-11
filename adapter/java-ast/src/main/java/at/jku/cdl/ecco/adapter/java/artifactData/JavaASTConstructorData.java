package at.jku.cdl.ecco.adapter.java.artifactData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaASTConstructorData extends JavaASTData {

	private String data;
	private List<String> modifiers;
	private List<String> parameters;
	private List<String> typeParameters;
	private List<String> throwExceptions;
	private List<String> annotations;

	public JavaASTConstructorData(String name) {
		this.data = name;
		this.modifiers = new ArrayList<>();
		this.parameters = new ArrayList<>();
		this.typeParameters = new ArrayList<>();
		this.throwExceptions = new ArrayList<>();
		this.annotations = new ArrayList<>();
		this.setType(ASTNodeType.CONSTRUCTOR_DECLARATION);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public List<String> getModifiers() {
		return Collections.unmodifiableList(modifiers);
	}

	public List<String> getParameters() {
		return Collections.unmodifiableList(parameters);
	}

	public List<String> getTypeParameters() {
		return Collections.unmodifiableList(typeParameters);
	}

	public List<String> getThrowExceptions() {
		return Collections.unmodifiableList(throwExceptions);
	}

	public String getName() {
		return data;
	}

	public List<String> getAnnotations() {
		return Collections.unmodifiableList(annotations);
	}

	public boolean addTypeParameter(String parameter) {
		if (parameter != null && !parameter.equals("")) {
			return typeParameters.add(parameter);
		}
		return false;
	}

	public Object addThrowException(String exception) {
		if (exception != null && !exception.equals("")) {
			return throwExceptions.add(exception);
		}
		return false;
	}

	public boolean addAnnotation(String annotation) {
		if (annotation != null && !annotation.equals("")) {
			return annotations.add(annotation);
		}
		return false;
	}

	public boolean addModifier(String modifier) {
		if (modifier != null && !modifier.equals("")) {
			return modifiers.add(modifier);
		}
		return false;
	}

	public boolean addParameter(String parameter) {
		if (parameter != null && !parameter.equals("")) {
			return parameters.add(parameter);
		}
		return false;
	}

	@Override
	public String toString() {
		return "[data=" + data + ", modifiers=" + modifiers + ", parameters=" + parameters
				+ ", typeParameters=" + typeParameters + ", throwExceptions=" + throwExceptions + ", annotations="
				+ annotations + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((annotations == null) ? 0 : annotations.hashCode());
		result = prime * result + ((data == null) ? 0 : data.hashCode());
		result = prime * result + ((modifiers == null) ? 0 : modifiers.hashCode());
		result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
		result = prime * result + ((throwExceptions == null) ? 0 : throwExceptions.hashCode());
		result = prime * result + ((typeParameters == null) ? 0 : typeParameters.hashCode());
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
		JavaASTConstructorData other = (JavaASTConstructorData) obj;
		if (annotations == null) {
			if (other.annotations != null)
				return false;
		} else if (!annotations.equals(other.annotations))
			return false;
		if (data == null) {
			if (other.data != null)
				return false;
		} else if (!data.equals(other.data))
			return false;
		if (modifiers == null) {
			if (other.modifiers != null)
				return false;
		} else if (!modifiers.equals(other.modifiers))
			return false;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
			return false;
		if (throwExceptions == null) {
			if (other.throwExceptions != null)
				return false;
		} else if (!throwExceptions.equals(other.throwExceptions))
			return false;
		if (typeParameters == null) {
			if (other.typeParameters != null)
				return false;
		} else if (!typeParameters.equals(other.typeParameters))
			return false;
		return true;
	}

}
