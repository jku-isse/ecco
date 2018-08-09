package at.jku.isse.ecco.adapter.java.test;

import org.junit.Test;

import java.util.function.Function;

import static at.jku.isse.ecco.adapter.java.test.JavaIntegrityTestHelper.*;

public class JavaIntegrityTests {

    @Test
    public void debugTest1() {
        integrityTest("VariablePool.java");
    }

    @Test(expected = Error.class)
    public void ensureFailure() {
        try {
            integrityTest("VariablePoolErr.java");
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void springIntegrityTest() {
        integrityTestSpring(
                "ResolvableType.java",
                "AnnotationWriter.java",
                "TypeReference.java"
        );
    }

    @Test
    public void simpleIntegrityTest1() {
        integrityTest(
                "Simple.java",
                "SimulationConfig_v5.java",
                "SwitchCase.java",
                "TestFile.java",
                "WindowSizes.java"
        );

        Function<String, String> a = b -> b;
    }

    @Test
    public void simpleIntegrityTest2() {
        integrityTest(
                "ArrayList.java",
                "JavaTreeArtifactData.java",
                "LoginRequired.java",
                "Looptest.java"
        );
    }

    @Test
    public void simpleIntegrityTest3() {
        integrityTest(
                "Constants.java",
                "InvisibleHandOverZoneImpl.java",
                "VariablePool.java",
                "Simple.java"
        );
    }

    @Test
    public void jdkIntegrityTest() {
        integrityTestJdk(
                "ArrayList.java",
                "BatchUpdateException.java",
                "List.java",
                "Math.java",
                "Proxy.java",
                "ReferencePipeline.java",
                "LocalTime.java",
                "Function.java"
        );
    }
}