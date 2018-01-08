package at.jku.isse.ecco.adapter.java.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
        {
                JavaIntegrityTests.class,
                RunAllWriterTests.class,
                RunAllReaderTests.class
        }
)
public class RunAllTests {
}
