package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

public class AssociationRepository extends AbstractRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssociationRepository.class);

    private EccoApplication application;

    public AssociationRepository() {
    }

    public AssociationRepository(EccoApplication eccoApplication) {
        this.application = eccoApplication;
    }

    public AssociationModel[] getAssociations() {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Association> associationCollection = eccoService.getRepository().getAssociations();
        ArrayList<AssociationModel> associationModels = new ArrayList<>();
        for (Association association : associationCollection) {
            associationModels.add(new AssociationModel(
                    association.getId(),
                    association.getAssociationString(),
                    association.computeCondition().getSimpleModuleConditionString(),
                    association.computeCondition().getSimpleModuleRevisionConditionString()
            ));
        }
        return associationModels.toArray(new AssociationModel[0]);
    }
}
