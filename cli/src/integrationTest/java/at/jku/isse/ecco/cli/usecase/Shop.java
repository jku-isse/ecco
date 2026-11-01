package at.jku.isse.ecco.cli.usecase;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * The variants the end-to-end tests commit, path to content: a base and one variant per feature,
 * each feature adding a line of its own to the shared {@code src/core.txt} at a place of its own,
 * and files of its own. Text, Markdown, a file without a final newline and a binary file.
 */
final class Shop {

    static final Map<String, byte[]> BASE = files(
            "README.md", "# Shop\n\nA small shop.\n",
            "src/core.txt", "start\nload\nsave\nstop\n",
            "notes.txt", "no final newline",
            "data/logo.bin", binary());

    static final Map<String, byte[]> BASE_SEARCH = with(BASE,
            "src/core.txt", "start\nload\nsearch\nsave\nstop\n",
            "src/search.txt", "query\nrank\n");

    static final Map<String, byte[]> BASE_EXPORT = with(BASE,
            "src/core.txt", "start\nload\nsave\nexport\nstop\n",
            "docs/export.md", "# Export\n\nWrites CSV.\n");

    static final Map<String, byte[]> BASE_LOGGING = with(BASE,
            "src/core.txt", "start\nlog\nload\nsave\nstop\n",
            "src/logging.txt", "level=info\n");

    /** Never committed: the search line from one variant, the export line from the other. */
    static final Map<String, byte[]> BASE_SEARCH_EXPORT = with(BASE,
            "src/core.txt", "start\nload\nsearch\nsave\nexport\nstop\n",
            "src/search.txt", "query\nrank\n",
            "docs/export.md", "# Export\n\nWrites CSV.\n");

    /** Never committed: the lines of all three features. */
    static final Map<String, byte[]> BASE_SEARCH_EXPORT_LOGGING = with(BASE_SEARCH_EXPORT,
            "src/core.txt", "start\nlog\nload\nsearch\nsave\nexport\nstop\n",
            "src/logging.txt", "level=info\n");

    /** Never committed: the lines of export and logging. */
    static final Map<String, byte[]> BASE_EXPORT_LOGGING = with(BASE_EXPORT,
            "src/core.txt", "start\nlog\nload\nsave\nexport\nstop\n",
            "src/logging.txt", "level=info\n");

    private Shop() {
    }

    private static Map<String, byte[]> files(Object... pathsAndContents) {
        return with(Map.of(), pathsAndContents);
    }

    private static Map<String, byte[]> with(Map<String, byte[]> files, Object... pathsAndContents) {
        Map<String, byte[]> result = new TreeMap<>(files);
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Object content = pathsAndContents[i + 1];
            result.put((String) pathsAndContents[i], content instanceof String text ? text.getBytes(StandardCharsets.UTF_8) : (byte[]) content);
        }
        return result;
    }

    private static byte[] binary() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) i;
        return bytes;
    }
}
