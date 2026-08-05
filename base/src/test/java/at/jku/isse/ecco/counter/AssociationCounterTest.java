package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AssociationCounterTest {

    private final EntityFactory ef = new SerEntityFactory();
    private final Repository.Op repository = repository();

    private Repository.Op repository() {
        Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(2);
        return repository;
    }

    private Module moduleFor(String featureName) {
        Feature feature = repository.addFeature(UUID.randomUUID().toString(), featureName);
        return repository.addModule(new Feature[]{feature}, new Feature[0]);
    }

    @Test
    public void addChildIsIdempotentForTheSameModule() {
        AssociationCounter counter = ef.createAssociation().getCounter();
        Module module = moduleFor("A");

        ModuleCounter first = counter.addChild(module);
        ModuleCounter second = counter.addChild(module);

        assertNotNull(first);
        assertNull(second, "addChild() returns null when the module is already tracked (Repository-wide idempotent-add convention)");
        assertEquals(1, counter.getChildren().size());
        assertSame(first, counter.getChild(module));
    }

    @Test
    public void addMergesCountAndChildCountersFromTheOtherCounter() {
        Association.Op associationA = ef.createAssociation();
        AssociationCounter counterA = associationA.getCounter();
        Module moduleShared = moduleFor("A");
        Module moduleOnlyInB = moduleFor("B");

        counterA.incCount(2);
        counterA.addChild(moduleShared).incCount(3);

        Association.Op associationB = ef.createAssociation();
        AssociationCounter counterB = associationB.getCounter();
        counterB.incCount(5);
        counterB.addChild(moduleShared).incCount(4);
        counterB.addChild(moduleOnlyInB).incCount(1);

        counterA.add(counterB);

        assertEquals(7, counterA.getCount(), "2 (own) + 5 (other)");
        assertEquals(7, counterA.getChild(moduleShared).getCount(), "3 (own) + 4 (other) for the module both counters already tracked");
        assertEquals(1, counterA.getChild(moduleOnlyInB).getCount(), "a module only the other counter had should be added, not just merged");
        assertEquals(2, counterA.getChildren().size());
    }

    @Test
    public void getAssociationCounterStringIncludesTheCount() {
        AssociationCounter counter = ef.createAssociation().getCounter();
        counter.incCount(3);

        assertTrue(counter.getAssociationCounterString().endsWith("(3)"));
    }

    /**
     * Known project bug (association-counter-unsynchronized-race in project memory): getChildren()
     * (SerAssociationCounter.java) returns a live view directly over the same Eclipse-Collections
     * UnifiedMap addChild() mutates, with no synchronization or defensive copy between them. Real
     * production crashes happen across threads (one thread iterating getChildren() while another
     * commits and calls addChild()), but the underlying defect - an unguarded live view, not a
     * snapshot - is fully reproducible in a single thread by mutating mid-iteration, which is what
     * this pins down deterministically rather than relying on a timing-dependent multi-threaded race
     * (which would make this test flaky).
     * <p>
     * Surprising detail worth knowing if this ever needs debugging in the wild: unlike
     * java.util.HashMap, Eclipse Collections' UnifiedMap does NOT do modCount-based fail-fast
     * detection - mutating during iteration does not throw the "expected"
     * ConcurrentModificationException. It throws ArrayIndexOutOfBoundsException instead (confirmed
     * empirically, not assumed), because the iterator's internal array-index bookkeeping silently
     * desyncs from the now-resized backing array. A real occurrence of this bug in production would
     * therefore surface as a confusing AIOOBE deep in Eclipse Collections' internals, not the
     * well-known "you mutated a collection during iteration" signal.
     * <p>
     * Not fixed here - see feedback-risk-methodology in project memory on escalating rather than
     * changing this synchronization as a side effect of adding tests (the project has deliberately
     * left this unfixed, twice, per explicit prior user choice).
     */
    @Test
    public void getChildrenViewIsNotSafeToIterateWhileMutating() {
        AssociationCounter counter = ef.createAssociation().getCounter();
        counter.addChild(moduleFor("A"));

        Iterator<ModuleCounter> iterator = counter.getChildren().iterator();
        iterator.next();
        counter.addChild(moduleFor("B")); // structural modification of the same backing map, mid-iteration

        assertThrows(ArrayIndexOutOfBoundsException.class, iterator::next);
    }
}
