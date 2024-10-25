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
        VevosUtils.sanitizeVevosFiles(destinationDirectoryPath, features);

        Path vevosFile1Path = destinationDirectoryPath.resolve("Variant_A/" + VevosUtils.VEVOS_FILENAME);
        List<String> lines1 = Files.readAllLines(vevosFile1Path);
        assertEquals(2, lines1.size());
        assertEquals("Path;File Condition;Block Condition;Presence Condition;Line Type;start;end", lines1.get(0));
        assertEquals("main.c;True;FEATUREA;FEATUREA;artifact;2;2", lines1.get(1));

        Path vevosFile2Path = destinationDirectoryPath.resolve("Variant_B/" + VevosUtils.VEVOS_FILENAME);
        List<String> lines2 = Files.readAllLines(vevosFile2Path);
        assertEquals(2, lines2.size());
        assertEquals("Path;File Condition;Block Condition;Presence Condition;Line Type;start;end", lines2.get(0));
        assertEquals("main.c;True;(FEATUREB && NONRELEVANT);(FEATUREB && NONRELEVANT);artifact;4;4", lines2.get(1));
    }
}
