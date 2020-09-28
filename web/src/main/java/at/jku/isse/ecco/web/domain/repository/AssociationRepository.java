package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.ArtifactsPerDepth;
import at.jku.isse.ecco.web.domain.model.AssociationArtifactsModel;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.domain.model.ModulesPerOrder;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

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

    public AssociationArtifactsModel[] getNumberOfArtifactsPerAssociation() {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Association> associationCollection = eccoService.getRepository().getAssociations();
        ArrayList<AssociationArtifactsModel> numberOfArtifactsPerAssociation = new ArrayList<>();
        for (Association association : associationCollection) {
            int numArtifacts = association.getRootNode().countArtifacts();
            if (numArtifacts > 0) {
                numberOfArtifactsPerAssociation.add(new AssociationArtifactsModel(association.getId(), numArtifacts));
            }
        }
        return numberOfArtifactsPerAssociation.toArray(new AssociationArtifactsModel[0]);
    }

    public ArtifactsPerDepth[] getArtifactsPerDepth() {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Association> associationCollection = eccoService.getRepository().getAssociations();
        ArrayList<ArtifactsPerDepth> listOfArtifactsPerDepth = new ArrayList<>();

        //Compositionknoten erstellen
        LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
        for (Association association : associationCollection) {
            //Diesem jede einzelne Artefaktwurzel hinufügen
            compRootNode.addOrigNode(association.getRootNode());
        }
        //Die Artefakte pro Tiefe berechnen...
        Map<Integer, Integer> artifactsPerDepth = compRootNode.countArtifactsPerDepth();
        for (Map.Entry<Integer, Integer> entry : artifactsPerDepth.entrySet()) {
            listOfArtifactsPerDepth.add(new ArtifactsPerDepth(entry.getValue(), entry.getKey()));
        }
        //...und anschließend in ein eigenes Datenmodell überführen und ausgeben
        return listOfArtifactsPerDepth.toArray(new ArtifactsPerDepth[0]);
    }

    public ModulesPerOrder[] getModulesPerOrder() {
        EccoService eccoService = this.application.getEccoService();
        Collection<? extends Association> associationCollection = eccoService.getRepository().getAssociations();
        ArrayList<ModulesPerOrder> modulesPerOrders = new ArrayList<>();

        final Map<Integer, Integer> modulesPerOrderMap = new TreeMap<>();
        for (Association association : associationCollection) {
            for (Module module : association.computeCondition().getModules().keySet()) {
                //For the specified order -> If no order of this type is seen yet, it must be the first. Otherwise 1 is added to the old value
                modulesPerOrderMap.compute(module.getOrder(), (key, oldValue) -> oldValue == null ? 1 : oldValue + 1);
            }
        }
        modulesPerOrderMap.forEach((key, value) -> {
            modulesPerOrders.add(new ModulesPerOrder(key, value));
        });
        return modulesPerOrders.toArray(new ModulesPerOrder[0]);
    }
}
