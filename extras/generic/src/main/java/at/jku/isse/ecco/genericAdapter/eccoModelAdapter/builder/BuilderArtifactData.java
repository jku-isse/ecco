package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder;


import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;
import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Michael Jahn
 */
public class BuilderArtifactData implements ArtifactData {

	public static final String RESOLVE_USES_REF_ID = "RESOLVE_USED_REF_ID";
	public static final String RESOLVE_OWN_REF_ID = "RESOLVE_OWN_REF_ID";

	private List<String> identifier;
	private String type;

	private List<String> printingValues;

	// optional refId, if references are supported by the strategy
	private Object refId;

	// NOTE: does not need to be persisted in the database, only used in {@link EccoModelBuilder}
	private List<TokenValue> parsedTokenValues;


	public BuilderArtifactData() {
		super();
	}


	public BuilderArtifactData(List<String> identifier, String type, List<TokenValue> parsedTokenValues, List<String> printingValues) {
		this.identifier = new ArrayList<>(identifier);
		this.type = type;
		this.parsedTokenValues = new ArrayList<>(parsedTokenValues);
		this.printingValues = new ArrayList<>(printingValues);
	}


	//    @Override
	public String getIdentifier() {
		StringBuilder str = new StringBuilder();
		identifier.forEach(str::append);
		/*for (ArtifactReference use : uses) {
			str.append(use.getTarget().getIdentifier());
        }*/
		return str.toString();
	}

	public List<String> getIdentifierList() {
		return Collections.unmodifiableList(identifier);
	}

	//    @Override
	public void updateIdentifier(String ident) {
		identifier.clear();
		identifier.add(ident);
	}

	//    @Override
	public String getType() {
		return type;
	}

	//    @Override
	public String getSource() {
		return null;
	}

	//    @Override
	public String getSourceType() {
		return null;
	}

	//    @Override
	public Object getObject() {
		return null;
	}

	@Override
	public String toString() {
		return getType() + ": " +  getIdentifier();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BuilderArtifactData that = (BuilderArtifactData) o;

		if (identifier != null ? !identifier.equals(that.identifier) : that.identifier != null) return false;
		if (type != null ? !type.equals(that.type) : that.type != null) return false;
//        if (uses != null ? !uses.equals(that.uses) : that.uses != null) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = identifier != null ? identifier.hashCode() : 0;
		result = 31 * result + (type != null ? type.hashCode() : 0);
//        result = 31 * result + (uses != null ? uses.hashCode() : 0);
		return result;
	}

	public Object getRefId() {
		return refId;
	}

	public void setRefId(Object refId) {
		this.refId = refId;
	}

	public List<String> getPrintingValues() {
		return printingValues;
	}

	public List<TokenValue> getParsedTokenValues() {
		return parsedTokenValues;
	}

	public List<ArtifactReference> getUses() {
		return null;
	}

	public void invalidateParsedTokenValues() {
		parsedTokenValues = null;
	}
}
