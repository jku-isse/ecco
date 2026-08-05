package at.jku.isse.ecco.module;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.module.SerCondition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ConditionTest {

    private final EntityFactory ef = new SerEntityFactory();
    private final Repository.Op repository = repository();

    private Repository.Op repository() {
        Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(2);
        return repository;
    }

    private FeatureRevision revisionOf(String featureName) {
        Feature feature = repository.addFeature(UUID.randomUUID().toString(), featureName);
        return feature.addRevision(UUID.randomUUID().toString());
    }

    @Test
    public void holdsIsTrueWhenAModuleRevisionOfTheConditionHolds() {
        FeatureRevision revisionA = revisionOf("A");
        Module module = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[0]);
        ModuleRevision moduleRevision = module.addRevision(new FeatureRevision[]{revisionA}, new Feature[0]);

        Condition condition = new SerCondition();
        condition.addModuleRevision(moduleRevision);

        Configuration matching = ef.createConfiguration(new FeatureRevision[]{revisionA});
        assertTrue(condition.holds(matching));

        Configuration empty = ef.createConfiguration(new FeatureRevision[0]);
        assertFalse(condition.holds(empty));
    }

    @Test
    public void containsChecksModuleAndModuleRevisionMembership() {
        FeatureRevision revisionA = revisionOf("A");
        FeatureRevision revisionB = revisionOf("B");
        Module moduleA = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[0]);
        Module moduleB = repository.addModule(new Feature[]{revisionB.getFeature()}, new Feature[0]);
        ModuleRevision moduleRevisionA = moduleA.addRevision(new FeatureRevision[]{revisionA}, new Feature[0]);

        Condition condition = new SerCondition();
        condition.addModuleRevision(moduleRevisionA);

        assertTrue(condition.contains(moduleA));
        assertTrue(condition.contains(moduleRevisionA));
        assertFalse(condition.contains(moduleB));
    }

    @Test
    public void addModuleWithoutARevisionRegistersAnEmptyEntry() {
        FeatureRevision revisionA = revisionOf("A");
        Module moduleA = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[0]);

        Condition condition = new SerCondition();
        condition.addModule(moduleA);

        assertTrue(condition.contains(moduleA));
        assertTrue(condition.getModules().get(moduleA).isEmpty());
    }

    @Test
    public void impliesIsTrueWhenEveryModuleOfTheOtherConditionIsImplied() {
        FeatureRevision revisionA = revisionOf("A");
        FeatureRevision revisionB = revisionOf("B");
        Module moduleA = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[0]);
        Module moduleAB = repository.addModule(new Feature[]{revisionA.getFeature(), revisionB.getFeature()}, new Feature[0]);
        ModuleRevision moduleRevisionA = moduleA.addRevision(new FeatureRevision[]{revisionA}, new Feature[0]);
        ModuleRevision moduleRevisionAB = moduleAB.addRevision(new FeatureRevision[]{revisionA, revisionB}, new Feature[0]);

        // "A" (the broader condition) implies "A AND B" (the narrower one): whenever A-and-B holds, A alone holds too.
        Condition broader = new SerCondition();
        broader.addModuleRevision(moduleRevisionA);

        Condition narrower = new SerCondition();
        narrower.addModuleRevision(moduleRevisionAB);

        assertTrue(broader.implies(narrower));
        assertFalse(narrower.implies(broader), "A-and-B does not imply A alone holding without B");
    }

    @Test
    public void moduleConditionStringsReflectTypeAndOrdering() {
        FeatureRevision revisionA = revisionOf("A");
        Module moduleA = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[0]);
        ModuleRevision moduleRevisionA = moduleA.addRevision(new FeatureRevision[]{revisionA}, new Feature[0]);

        Condition condition = new SerCondition();
        condition.setType(Condition.TYPE.OR);
        condition.addModuleRevision(moduleRevisionA);

        assertEquals(moduleA.toString(), condition.getModuleConditionString());
        assertEquals("[" + moduleRevisionA + "]", condition.getModuleRevisionConditionString());
        assertTrue(condition.toString().contains(moduleRevisionA.toString()));
    }
}
