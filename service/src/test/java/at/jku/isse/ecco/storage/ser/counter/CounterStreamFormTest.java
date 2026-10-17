package at.jku.isse.ecco.storage.ser.counter;

import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The plain serialized form of the counters - what fetch, pull and push send, and what files written
 * before compact counters hold - stays the same whatever the classes keep in memory: the fields
 * and serialVersionUID in the stream decide whether another build reads it.
 */
public class CounterStreamFormTest {

    private static List<String> form(Class<?> type) {
        ObjectStreamClass descriptor = ObjectStreamClass.lookup(type);
        return Arrays.stream(descriptor.getFields()).map(f -> f.getName() + ":" + f.getTypeString() + (f.isPrimitive() ? f.getTypeCode() : "")).toList();
    }

    @Test
    public void theModuleCounterIsSentAsItWas() {
        assertEquals(1L, ObjectStreamClass.lookup(SerModuleCounter.class).getSerialVersionUID());
        assertEquals(List.of("count:nullI", "children:Ljava/util/Collection;", "module:Lat/jku/isse/ecco/storage/ser/module/SerModule;"), form(SerModuleCounter.class));
    }

    @Test
    public void theModuleRevisionCounterIsSentAsItWas() {
        assertEquals(1L, ObjectStreamClass.lookup(SerModuleRevisionCounter.class).getSerialVersionUID());
        assertEquals(List.of("count:nullI", "moduleRevision:Lat/jku/isse/ecco/storage/ser/module/SerModuleRevision;"), form(SerModuleRevisionCounter.class));
    }
}
