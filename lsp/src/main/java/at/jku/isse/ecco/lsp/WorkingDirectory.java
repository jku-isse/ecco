package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.dao.EccoUtil;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.dispatch.DispatchReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * What the editor's checkout may replace in a working directory. A checkout writes only into a
 * folder empty apart from .ecco, so the files there must go first - but only those the last checkout
 * (or commit from the editor) wrote, as its {@code .hashes} lists them with their SHA-1, and ECCO's
 * own .config, .warnings and .hashes. A file changed since is listed, to be replaced only when the
 * user says so; anything else - a file not from the checkout, .git, .vscode - stops it, never deleted.
 */
final class WorkingDirectory {

    /** Never deleted, and a checkout cannot be made beside them: version control and editor folders. */
    static final Set<String> PROTECTED = Set.of(".git", ".svn", ".hg", ".idea", ".vscode");
    /** Deleted with the checkout's files: written by macOS, read by no commit. */
    static final String DS_STORE = ".DS_Store";

    /**
     * The working directory against its last checkout: files changed and removed since, entries not
     * from it (relative paths), and why it cannot be told (no .hashes), or null.
     */
    record Changes(List<String> changed, List<String> removed, List<String> foreign, String problem) {
        boolean replaceable() {
            return this.problem == null && this.foreign.isEmpty();
        }

        boolean unchanged() {
            return this.replaceable() && this.changed.isEmpty() && this.removed.isEmpty();
        }
    }

    private WorkingDirectory() {
    }

    /** The files of the last checkout, by their path relative to [dir], from its .hashes; null without one. */
    static Map<Path, String> checkedOut(Path dir) throws IOException {
        Path file = dir.resolve(EccoService.HASHES_FILE_NAME);
        if (!Files.isRegularFile(file))
            return null;
        Properties hashes = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            hashes.load(reader);
        }
        Map<Path, String> files = new TreeMap<>();
        for (String key : hashes.stringPropertyNames()) {
            Path path = dir.resolve(key).normalize(); // keys are absolute; relative ones are taken from dir
            if (!path.startsWith(dir))
                return null; // the folder moved since: its record names other files
            files.put(dir.relativize(path), hashes.getProperty(key));
        }
        return files;
    }

    static Changes since(Path dir) throws IOException {
        Map<Path, String> checkedOut = checkedOut(dir);
        if (checkedOut == null)
            return new Changes(List.of(), List.of(), List.of(), "There is no record of the files the last checkout wrote (" + EccoService.HASHES_FILE_NAME
                    + " is missing, or names files of another folder): check out with the GUI or the command line, which delete the folder's contents.");
        List<String> changed = new ArrayList<>(), removed = new ArrayList<>(), foreign = new ArrayList<>();
        for (Map.Entry<Path, String> file : checkedOut.entrySet()) {
            Path path = dir.resolve(file.getKey());
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                removed.add(file.getKey().toString());
            else if (!file.getValue().equals(EccoUtil.getSHA(path)))
                changed.add(file.getKey().toString());
        }
        for (Path relative : entries(dir)) {
            if (isProtected(relative))
                foreign.add(relative + "/");
            else if (!checkedOut.containsKey(relative))
                foreign.add(relative.toString());
        }
        return new Changes(changed, removed, foreign, null);
    }

    /**
     * Deletes what [changes] found replaceable: the files of the last checkout, ECCO's own files, the
     * .DS_Store files, and the folders left empty - nothing else.
     */
    static void clear(Path dir, Changes changes) throws IOException {
        if (!changes.replaceable())
            throw new IllegalStateException("Not replaceable: " + changes);
        for (Path file : checkedOut(dir).keySet())
            Files.deleteIfExists(dir.resolve(file));
        for (Path file : List.of(EccoService.CONFIG_FILE_NAME, EccoService.WARNINGS_FILE_NAME, EccoService.HASHES_FILE_NAME))
            Files.deleteIfExists(dir.resolve(file));
        List<Path> folders = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path folder, java.nio.file.attribute.BasicFileAttributes attributes) {
                Path relative = dir.relativize(folder);
                if (relative.toString().isEmpty())
                    return FileVisitResult.CONTINUE;
                if (relative.startsWith(EccoService.REPOSITORY_DIR_NAME) || isProtected(relative))
                    return FileVisitResult.SKIP_SUBTREE;
                folders.add(folder);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (file.getFileName().toString().equals(DS_STORE) && attributes.isRegularFile())
                    Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
        // deepest first; a folder still holding something is kept - and the checkout then refuses
        folders.sort(Comparator.comparingInt(Path::getNameCount).reversed());
        for (Path folder : folders)
            try (Stream<Path> entries = Files.list(folder)) {
                if (entries.findAny().isEmpty())
                    Files.delete(folder);
            }
    }

    /**
     * Records the files of [dir] a commit read - those [reader] does not ignore, but ECCO's own and the
     * protected folders - as the checkout's in .hashes: after a commit from the editor they are
     * committed, and a later checkout may replace them.
     */
    static void recordCommitted(Path dir, DispatchReader reader) throws IOException {
        Properties hashes = new Properties();
        record(dir, dir, reader, hashes);
        Path file = dir.resolve(EccoService.HASHES_FILE_NAME);
        try (Writer writer = Files.newBufferedWriter(file)) {
            hashes.store(writer, null);
        }
    }

    private static void record(Path dir, Path current, DispatchReader reader, Properties hashes) throws IOException {
        try (Stream<Path> entries = Files.list(current)) {
            for (Path path : (Iterable<Path>) entries::iterator) {
                Path relative = dir.relativize(path);
                if (relative.startsWith(EccoService.REPOSITORY_DIR_NAME) || isEccoFile(relative) || isProtected(relative) || reader.isIgnored(relative))
                    continue;
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    record(dir, path, reader, hashes);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    hashes.put(path.toString(), EccoUtil.getSHA(path));
            }
        }
    }

    /**
     * The files of [dir] (relative), without .ecco, ECCO's own files and .DS_Store - and the protected
     * folders as themselves, not what is in them.
     */
    private static List<Path> entries(Path dir) throws IOException {
        List<Path> entries = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path folder, java.nio.file.attribute.BasicFileAttributes attributes) {
                Path relative = dir.relativize(folder);
                if (relative.toString().isEmpty())
                    return FileVisitResult.CONTINUE;
                if (relative.startsWith(EccoService.REPOSITORY_DIR_NAME))
                    return FileVisitResult.SKIP_SUBTREE;
                if (isProtected(relative)) {
                    entries.add(relative);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) {
                Path relative = dir.relativize(file);
                if (!isEccoFile(relative) && !relative.getFileName().toString().equals(DS_STORE))
                    entries.add(relative);
                return FileVisitResult.CONTINUE;
            }
        });
        return entries;
    }

    private static boolean isEccoFile(Path relative) {
        return relative.getNameCount() == 1 && (relative.equals(EccoService.CONFIG_FILE_NAME) || relative.equals(EccoService.WARNINGS_FILE_NAME)
                || relative.equals(EccoService.HASHES_FILE_NAME));
    }

    private static boolean isProtected(Path relative) {
        return PROTECTED.contains(relative.getName(0).toString());
    }
}
