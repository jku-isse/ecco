package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.feature.ConfigurationText.Entry;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The configuration grammar and printing, in the one place they are defined. */
public class ConfigurationTextTest {

    @Test
    public void entriesAreRead() {
        assertEquals(List.of(
                new Entry("A", false, null, false),
                new Entry("B", false, "2", false),
                new Entry("C", false, null, true),
                new Entry("f-1", true, "3", false)), ConfigurationText.parse("  A, B.2 ,C', [f-1].3 \n"));
        assertEquals(List.of(), ConfigurationText.parse(" "));
        for (String invalid : List.of("A,", "A, , B", "A.1.2", "A B", "A.'"))
            assertThrows(IllegalArgumentException.class, () -> ConfigurationText.parse(invalid), invalid);
        assertEquals(List.of(new Entry("A", false, "1", false)), ConfigurationText.parseRevisions("A.1"));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationText.parseRevisions("A"), "a revision is needed");
    }

    @Test
    public void revisionsArePrintedSortedWithFullOrDisplayedIds() {
        FeatureRevision b = new SerFeature("b", "B").addRevision("0123456789");
        FeatureRevision a = new SerFeature("a", "A").addRevision("2");
        assertEquals("A.2, B.0123456789", ConfigurationText.print(List.of(b, a), true));
        assertEquals("A.2, B.0123456", ConfigurationText.print(List.of(b, a), false));
        String printed = ConfigurationText.print(List.of(b, a), true);
        assertEquals(List.of(new Entry("A", false, "2", false), new Entry("B", false, "0123456789", false)), ConfigurationText.parse(printed), "parsed again");
    }

    @Test
    public void entriesAreAddedWithoutAStrayComma() {
        assertEquals("A, B", ConfigurationText.with("A", " B "));
        assertEquals("B", ConfigurationText.with("", "B"), "an empty variant made \",B\", which does not parse");
        assertEquals("A", ConfigurationText.with("A ", null));
        assertEquals(List.of("A", "-B"), List.copyOf(ConfigurationText.tokens(" A, ,-B,A")));
    }
}
