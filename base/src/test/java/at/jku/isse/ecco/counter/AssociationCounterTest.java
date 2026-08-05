package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
     * Regression test for a real, twice-recurring production crash (association-counter-
     * unsynchronized-race in project memory): getChildren() used to return a live view directly over
     * the same Eclipse-Collections UnifiedMap addChild() mutates, with no synchronization or defensive
     * copy between them - one thread iterating getChildren() while another thread (a commit's
     * background write) called addChild() crashed with ArrayIndexOutOfBoundsException (confirmed
     * empirically - Eclipse Collections' UnifiedMap does not do java.util's modCount-based fail-fast,
     * so it doesn't throw the "expected" ConcurrentModificationException either). Fixed by making
     * getChildren() return a defensive copy and synchronizing every read/write of the backing map -
     * mutating after obtaining the iterator must no longer be able to affect that already-copied
     * collection at all, which is exactly what this pins down: no exception, and the snapshot stays
     * exactly as it was when it was taken.
     */
    @Test
    public void getChildrenReturnsASnapshotUnaffectedByLaterMutation() {
        AssociationCounter counter = ef.createAssociation().getCounter();
        counter.addChild(moduleFor("A"));

        Iterator<ModuleCounter> iterator = counter.getChildren().iterator();
        iterator.next();
        counter.addChild(moduleFor("B")); // structural modification of the backing map, after the snapshot was taken

        assertFalse(iterator.hasNext(), "the snapshot taken by getChildren() must be unaffected by the later addChild()");
        assertEquals(2, counter.getChildren().size(), "a fresh call to getChildren() does see the new child");
    }

    /**
     * The actual real-world scenario the fix targets: one thread repeatedly reading getChildren()
     * (standing in for GUI rendering via Association.Op#computeLikelyCondition()) concurrently with
     * another thread calling addChild() (standing in for a commit's background write) - see
     * association-counter-unsynchronized-race in project memory for the real crash this reproduced.
     * Before the fix this reliably threw ArrayIndexOutOfBoundsException within a handful of
     * iterations; after it, this test must complete cleanly every time.
     */
    @Test
    @Timeout(30)
    public void concurrentReadsAndWritesDoNotCorruptTheCounter() throws InterruptedException {
        AssociationCounter counter = ef.createAssociation().getCounter();
        int writerModuleCount = 500;

        Thread writer = new Thread(() -> {
            for (int i = 0; i < writerModuleCount; i++) {
                counter.addChild(moduleFor("W" + i));
            }
        });

        final Throwable[] readerFailure = new Throwable[1];
        Thread reader = new Thread(() -> {
            try {
                while (writer.isAlive()) {
                    for (ModuleCounter moduleCounter : counter.getChildren()) {
                        moduleCounter.getCount(); // exercise the snapshot, same shape as computeLikelyCondition()
                    }
                }
            } catch (Throwable t) {
                readerFailure[0] = t;
            }
        });

        writer.start();
        reader.start();
        writer.join();
        reader.join();

        assertNull(readerFailure[0], "reading getChildren() concurrently with addChild() must never throw");
        assertEquals(writerModuleCount, counter.getChildren().size());
    }
}
