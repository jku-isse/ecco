package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.service.AdapterPreferences;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When two adapters claimed the same pattern at the same priority, a new repository's .adapters
 * routed the files to whichever adapter Guice's reader set happened to list first - C and C++ both
 * claimed *.c and *.h at the top priority. Ties are now broken by plugin id, and C++ yields *.c
 * and *.h to C (it keeps *.cpp and *.hpp), as the README describes.
 */
public class DispatchReaderPriorityTieTest {

    private static final String DISABLED_ADAPTERS_KEY = "disabledAdapterPluginIds";

    @Test
    public void tiesAreBrokenByPluginIdWhateverOrderTheReadersComeIn(@TempDir Path tmp) throws IOException {
        List<String> expected = List.of("a.Plugin;**.c", "z.Plugin;**.c", "b.Plugin;**.c");
        for (boolean reversed : new boolean[]{false, true}) {
            Set<ArtifactReader<Path, Set<Node.Op>>> readers = new LinkedHashSet<>();
            List<ArtifactReader<Path, Set<Node.Op>>> fakes = List.of(
                    fake("z.Plugin", Map.of(Integer.MAX_VALUE, new String[]{"**.c"})),
                    fake("a.Plugin", Map.of(Integer.MAX_VALUE, new String[]{"**.c"})),
                    fake("b.Plugin", Map.of(1, new String[]{"**.c"})));
            if (reversed)
                for (int i = fakes.size() - 1; i >= 0; i--) readers.add(fakes.get(i));
            else
                readers.addAll(fakes);

            Path repositoryDir = Files.createDirectories(tmp.resolve("repo-" + reversed));
            new DispatchReader(new SerEntityFactory(), readers, repositoryDir).init();

            assertEquals(expected, Files.readAllLines(repositoryDir.resolve(".adapters")), "reversed=" + reversed);
        }
    }

    @Test
    @Timeout(60)
    public void cFilesGoToTheCAdapterAndCppFilesToTheCppAdapter(@TempDir Path tmp) throws IOException {
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("a.c"), "int a(void) { return 1; }\n");
        Files.writeString(content.resolve("a.h"), "int a(void);\n");
        Files.writeString(content.resolve("b.cpp"), "int b() { return 2; }\n");

        // the shipped defaults, not this machine's stored adapter choices
        Preferences prefs = Preferences.userNodeForPackage(AdapterPreferences.class);
        String stored = prefs.get(DISABLED_ADAPTERS_KEY, null);
        prefs.remove(DISABLED_ADAPTERS_KEY);
        try {
            commitAndCheckRouting(tmp, content);
        } finally {
            if (stored != null)
                prefs.put(DISABLED_ADAPTERS_KEY, stored);
            try {
                prefs.flush();
            } catch (BackingStoreException ignored) {
            }
        }
    }

    private static void commitAndCheckRouting(Path tmp, Path content) {
        Map<Path, String> pluginIdByFile = new HashMap<>();
        EccoListener listener = new EccoListener() {
            @Override
            public void fileReadEvent(Path file, ArtifactReader reader) {
                pluginIdByFile.put(file, reader.getPluginId());
            }
        };
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            service.addListener(listener);
            service.commit("c and c++", "A");
        }

        assertEquals("at.jku.isse.ecco.adapter.c.CPlugin", pluginIdByFile.get(Path.of("a.c")));
        assertEquals("at.jku.isse.ecco.adapter.c.CPlugin", pluginIdByFile.get(Path.of("a.h")));
        assertEquals("at.jku.isse.ecco.adapter.cpp.CppPlugin", pluginIdByFile.get(Path.of("b.cpp")));
    }

    private static ArtifactReader<Path, Set<Node.Op>> fake(String pluginId, Map<Integer, String[]> patterns) {
        return new ArtifactReader<>() {
            @Override
            public String getPluginId() {
                return pluginId;
            }

            @Override
            public Map<Integer, String[]> getPrioritizedPatterns() {
                return patterns;
            }

            @Override
            public Set<Node.Op> read(Path base, Path[] input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<Node.Op> read(Path[] input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void addListener(ReadListener listener) {
            }

            @Override
            public void removeListener(ReadListener listener) {
            }
        };
    }
}
