import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.experiment.utils.vevos.VevosUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VevosSamplingTest {

    @Test
    public void sanitizeVevosFileTest() throws IOException {
        Path sourceDirectoryPath = ResourceUtils.getResourceFolderPath("Variants_To_Be_Sanitized");
        Path destinationDirectoryPath = sourceDirectoryPath.resolve("../Sanitation_Folder");
        String sourceDirectoryLocation = sourceDirectoryPath.toString();
        String destinationDirectoryLocation = destinationDirectoryPath.toString();
        File sourceDirectory = new File(sourceDirectoryLocation);
        File destinationDirectory = new File(destinationDirectoryLocation);

        if (destinationDirectory.exists()){
            FileUtils.deleteDirectory(destinationDirectory);
        }
        FileUtils.copyDirectory(sourceDirectory, destinationDirectory);

        List<String> features = new LinkedList<>();
        features.add("FEATUREA");
        features.add("FEATUREB");
        features.add("TeORst");
        features.add("TestOR");
        features.add("OR");
        VevosUtils.sanitizeVevosFiles(destinationDirectoryPath, features);

        Path vevosFile1Path = destinationDirectoryPath.resolve("Variant_A/" + VevosUtils.VEVOS_FILENAME);
        List<String> lines1 = Files.readAllLines(vevosFile1Path);
        assertEquals(3, lines1.size());
        assertEquals("Path;File Condition;Block Condition;Presence Condition;Line Type;start;end", lines1.get(0));
        assertEquals("main.c;True;(FEATUREA && TestOR);(FEATUREA && TestOR);artifact;2;2", lines1.get(1));
        assertEquals("main.c;True;(FEATUREA || NONRELAVENT || TeORst);(FEATUREA || NONRELAVENT || TeORst);artifact;2;2", lines1.get(2));

        Path vevosFile2Path = destinationDirectoryPath.resolve("Variant_B/" + VevosUtils.VEVOS_FILENAME);
        List<String> lines2 = Files.readAllLines(vevosFile2Path);
        assertEquals(2, lines2.size());
        assertEquals("Path;File Condition;Block Condition;Presence Condition;Line Type;start;end", lines2.get(0));
        assertEquals("main.c;True;(FEATUREB && NONRELEVANT && OR);(FEATUREB && NONRELEVANT && OR);artifact;4;4", lines2.get(1));


        VevosUtils.sanitizeVevosConfigFiles(destinationDirectoryPath);

        Path configFile1Path = destinationDirectoryPath.resolve("configs/Variant_A.config");
        List<String> lines3 = Files.readAllLines(configFile1Path);
        assertEquals(3, lines3.size());
        assertEquals("TeORst", lines3.get(0));
        assertEquals("FEATUREA", lines3.get(1));
        assertEquals("TestOR", lines3.get(2));

        Path configFile2Path = destinationDirectoryPath.resolve("configs/Variant_B.config");
        List<String> lines4 = Files.readAllLines(configFile2Path);
        assertEquals(2, lines4.size());
        assertEquals("FEATUREB", lines4.get(0));
        assertEquals("OR", lines4.get(1));
    }

}
