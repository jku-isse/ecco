package at.jku.isse.ecco.test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The variants the end-to-end tests of the use cases commit (the command line's, the REST server's), path to content: a base and one variant per feature,
 * each feature adding a line of its own to the shared {@code src/core.txt} at a place of its own,
 * and files of its own. Text, Markdown, a file without a final newline and a binary file.
 */
public final class Shop {

    public static final Map<String, byte[]> BASE = files(
            "README.md", "# Shop\n\nA small shop.\n",
            "src/core.txt", "start\nload\nsave\nstop\n",
            "notes.txt", "no final newline",
            "data/logo.bin", binary());

    public static final Map<String, byte[]> BASE_SEARCH = with(BASE,
            "src/core.txt", "start\nload\nsearch\nsave\nstop\n",
            "src/search.txt", "query\nrank\n");

    public static final Map<String, byte[]> BASE_EXPORT = with(BASE,
            "src/core.txt", "start\nload\nsave\nexport\nstop\n",
            "docs/export.md", "# Export\n\nWrites CSV.\n");

    public static final Map<String, byte[]> BASE_LOGGING = with(BASE,
            "src/core.txt", "start\nlog\nload\nsave\nstop\n",
            "src/logging.txt", "level=info\n");

    /** Never committed: the search line from one variant, the export line from the other. */
    public static final Map<String, byte[]> BASE_SEARCH_EXPORT = with(BASE,
            "src/core.txt", "start\nload\nsearch\nsave\nexport\nstop\n",
            "src/search.txt", "query\nrank\n",
            "docs/export.md", "# Export\n\nWrites CSV.\n");

    /** Never committed: the lines of all three features. */
    public static final Map<String, byte[]> BASE_SEARCH_EXPORT_LOGGING = with(BASE_SEARCH_EXPORT,
            "src/core.txt", "start\nlog\nload\nsearch\nsave\nexport\nstop\n",
            "src/logging.txt", "level=info\n");

    /** Never committed: the lines of export and logging. */
    public static final Map<String, byte[]> BASE_EXPORT_LOGGING = with(BASE_EXPORT,
            "src/core.txt", "start\nlog\nload\nsave\nexport\nstop\n",
            "src/logging.txt", "level=info\n");

    /** The configurations the sync tests check: the committed ones and two never committed as such. */
    public static final Map<String, Map<String, byte[]>> ALL = configurations(
            "BASE", BASE,
            "BASE, search", BASE_SEARCH,
            "BASE, export", BASE_EXPORT,
            "BASE, logging", BASE_LOGGING,
            "BASE, export, logging", BASE_EXPORT_LOGGING,
            "BASE, search, export, logging", BASE_SEARCH_EXPORT_LOGGING);

    /** {@link #ALL} without search. */
    public static final Map<String, Map<String, byte[]>> WITHOUT_SEARCH = configurations(
            "BASE", BASE,
            "BASE, logging", BASE_LOGGING,
            "BASE, export", BASE_EXPORT,
            "BASE, export, logging", BASE_EXPORT_LOGGING);

    private Shop() {
    }

    /** The configuration a committed variant is committed with. */
    public static String configuration(Map<String, byte[]> variant) {
        if (variant == BASE) return "BASE";
        if (variant == BASE_SEARCH) return "BASE, search";
        if (variant == BASE_EXPORT) return "BASE, export";
        if (variant == BASE_LOGGING) return "BASE, logging";
        throw new IllegalArgumentException("not a committed variant");
    }

    /** Whether the configuration is one a variant is committed with. */
    public static boolean committed(String configuration) {
        return List.of("BASE", "BASE, search", "BASE, export", "BASE, logging").contains(configuration);
    }

    private static Map<String, Map<String, byte[]>> configurations(Object... configurationsAndVariants) {
        Map<String, Map<String, byte[]>> result = new LinkedHashMap<>();
        for (int i = 0; i < configurationsAndVariants.length; i += 2) {
            @SuppressWarnings("unchecked")
            Map<String, byte[]> variant = (Map<String, byte[]>) configurationsAndVariants[i + 1];
            result.put((String) configurationsAndVariants[i], variant);
        }
        return result;
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
