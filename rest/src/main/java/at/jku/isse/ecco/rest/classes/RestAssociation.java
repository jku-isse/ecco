package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.core.Association;

public class RestAssociation {

    private final Association association;

    public RestAssociation(Association association) {
        this.association = association;
    }

    public String getId() {
        return association.getId();
    }

    public String getSimpleModuleRevisionCondition() { // just simplified at this point
        return association.computeCondition().getSimpleModuleRevisionConditionString();
    }

}
