package at.jku.isse.ecco.adapter.challenge.test;

import at.jku.isse.ecco.adapter.challenge.JavaChallengeReader;
import at.jku.isse.ecco.adapter.challenge.test.utils.ResourceUtils;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChallengeReaderTest {

    private Collection<Path> getRelativeDirContent(JavaChallengeReader reader, Path dir){
        Map<Integer, String[]> prioritizedPatterns = reader.getPrioritizedPatterns();
        String[] patterns = prioritizedPatterns.values().iterator().next();
        Collection<PathMatcher> pathMatcher = Arrays.stream(patterns)
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .toList();

        Set<Path> fileSet = new HashSet<>();
        try (Stream<Path> pathStream = Files.walk(dir)) {
            pathStream.forEach( path -> {
                Boolean applicableFile = pathMatcher.stream().map(pm -> pm.matches(path)).reduce(Boolean::logicalOr).get();
                if (applicableFile) {
                    fileSet.add(path);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fileSet.stream().map(dir::relativize).collect(Collectors.toList());
    }

    @Test
    public void readerReadsAllJavaFiles(){
        Path variantFolderPath = ResourceUtils.getResourceFolderPath("test_variant");
        JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());
        Collection<Path> relativeFiles = this.getRelativeDirContent(reader, variantFolderPath);
        Path[] relativeFileAr = relativeFiles.toArray(new Path[0]);

        Set<Node.Op> nodes = reader.read(variantFolderPath, relativeFileAr);
        assertEquals(2, nodes.size());
    }

    @Test
    public void readingJavaFilesWithoutTraces(){
        Path variantFolderPath = ResourceUtils.getResourceFolderPath("test_variant");
        JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());
        Collection<Path> relativeFiles = this.getRelativeDirContent(reader, variantFolderPath);
        Path[] relativeFileAr = relativeFiles.toArray(new Path[0]);

        Set<Node.Op> nodes = reader.read(variantFolderPath, relativeFileAr);
        assertEquals(2, nodes.size());
    }

    // todo: test with traces
}
