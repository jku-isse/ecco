package at.jku.isse.ecco.adapter.lilypond;
import javafx.scene.image.Image;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LilypondCompiler {
    protected static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private final Path startupPath = Path.of(System.getProperty("user.dir"));
    private final Path basePath = startupPath.getParent();
    private final static Path lilypond_exe = getLilypondPath();
    private final static Path[] lilypond_searchPaths = getLilypondSearchPaths();

    private final Path workingDir = basePath.getParent().resolve("compiled");
    private final String inFile = "input.ly";
    private final String outName = "image";
    private final String outFile = "image.cropped.png";
    private String lastError;

    public String getLastError() {
        return lastError;
    }

    public LilypondCompiler(String lyCode) {

        if (Files.notExists(workingDir)) {
            try {
                Files.createDirectories(workingDir);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "could not create directory for compiled files\n", e);
            }
        }

        try {
            Files.writeString(workingDir.resolve(inFile), lyCode);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "error on writing lilypond file for compilation\n", e);
        }
    }

    public Image compilePNG() {
        lastError = null;
        ProcessBuilder lilycmd=new ProcessBuilder(lilypond_exe.toString(), "-fpng", "-dcrop");
        for (Path p : lilypond_searchPaths) {
            lilycmd.command().add("-I");
            lilycmd.command().add(p.toString());
        }
        lilycmd.command().add("-o");
        lilycmd.command().add(workingDir.resolve(outName).toString()); // output
        lilycmd.command().add(workingDir.resolve(inFile).toString()); // input
        lilycmd.directory(workingDir.toFile());
        Process process = null;
        Image image = null;
        try {
            process = lilycmd.start();
            final BufferedReader errRd = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            StringJoiner sjErr = new StringJoiner(System.getProperty("line.separator"));
            errRd.lines().iterator().forEachRemaining(sjErr::add);

            if (LOGGER.isLoggable(Level.FINE)) {
                final BufferedReader stdRd = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                StringJoiner sjStd = new StringJoiner(System.getProperty("line.separator"));
                stdRd.lines().iterator().forEachRemaining(sjStd::add);

                if (sjStd.length() > 0) LOGGER.fine(sjStd.toString());
            }

            int exitCode = -1;
            if (process.waitFor(15, TimeUnit.SECONDS)) {
                exitCode = process.exitValue();
            } else {
                lastError = "compiling lilypond file to png timed out after 15 seconds";
                LOGGER.fine(lastError);
            }

            if (exitCode == 0) {
                LOGGER.log(Level.FINE, "Lilypond exited normal, code: {0}", exitCode);
                if (Files.notExists(workingDir.resolve(outFile))) {
                    lastError = "Lilypond could not create an image.";
                } else {
                    image = new Image(Files.newInputStream(workingDir.resolve(outFile)));
                }

            } else {
                lastError = sjErr.toString();
                LOGGER.log(Level.FINE, "Lilypond exited with code {0}:\n{1}", new Object[] { exitCode, lastError });
            }

        } catch (IOException | InterruptedException e) {
            lastError = e.getMessage();
            LOGGER.log(Level.SEVERE, lastError, e);

        } finally {
            if (process != null) process.destroy();
        }

        return image;
    }

    public static Path LilypondPath() {
        return lilypond_exe;
    }
    public static Path[] LilypondSearchPaths() {
        return lilypond_searchPaths;
    }

    private static Path getLilypondPath() {
        String path = getProperty("lilypond_executable");
        if (path != null) {
            return Path.of(path);
        }
        return null;
    }
    private static Path[] getLilypondSearchPaths() {
        String prop = getProperty("lilypond_search_paths");
        if (prop != null && !prop.isEmpty()) {
            String[] paths = prop.split("|");
            Path[] result = new Path[paths.length];
            for (int i = 0; i < paths.length; i++) {
                result[i] = Path.of(paths[i]);
            }
            return result;
        }
        return new Path[]{};
    }

    private static Properties PROPERTIES;
    private static String getProperty(String key) {
        String configFile = "lilypond-config.properties";
        if (PROPERTIES == null) {
            PROPERTIES = new Properties();
            try (InputStream in = LilypondCompiler.class.getClassLoader().getResourceAsStream(configFile)) {
                if (in == null) {
                    LOGGER.log(Level.INFO, "configuration file " + configFile + " not found");
                    return null;
                }
                PROPERTIES.load(in);

            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "could not load properties file", ex);
            }
        }

        if (!PROPERTIES.containsKey(key)) {
            LOGGER.log(Level.SEVERE, "missing property '" + key + "' in config file '" + configFile + "'");
        }

        return PROPERTIES.getProperty(key);
    }
}
