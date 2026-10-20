package at.jku.isse.ecco.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * What a test compares to show that an operation left a directory alone: every file and directory
 * under it, by relative path, with its content hash and modification time.
 */
public final class DirectoryFingerprint {

    private DirectoryFingerprint() {
    }

    /** relative path -> content hash and modification time ("/" ends a directory's path) */
    public static Map<String, String> of(Path dir) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.toList()) {
                String key = dir.relativize(p).toString();
                String time = Files.getLastModifiedTime(p).toString();
                if (Files.isDirectory(p))
                    result.put(key + "/", time);
                else
                    result.put(key, sha256(Files.readAllBytes(p)) + " " + time);
            }
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
