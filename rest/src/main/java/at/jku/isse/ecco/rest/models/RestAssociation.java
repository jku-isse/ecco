package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.core.Association;

public class RestAssociation {
    private final Association association;

    public RestAssociation(Association association) {
        this.association = association;
    }

    public String getId() {
        return association.getId();
    }

    public String getSimpleModuleRevisionCondition() {
        return association.computeCondition().getSimpleModuleRevisionConditionString();
    }
}
