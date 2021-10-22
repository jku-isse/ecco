package at.jku.isse.ecco.adapter.lilypond;
import javafx.scene.image.Image;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LilypondCompiler {
    protected static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private Path startupPath = Path.of(System.getProperty("user.dir"));
    private Path basePath = startupPath.getParent();
    /**
     * Configure path to lilypond executable file
     * TODO: move path configuration to gradle build file
     */
    private Path lilypond_exe = Path.of("C:/Program Files (x86)/LilyPond/usr/bin/lilypond-windows");
    private Path[] lilypond_searchPaths = new Path[]{basePath.resolve("lytests/sulzer")};

    private Path workingDir = basePath.getParent().resolve("lytests/compilerTest/");
    private String inFile = "input.ly";
    private String outName = "image";
    private String outFile = "image.cropped.png";
    private String lastError;

    public String getLastError() {
        return lastError;
    }

    public LilypondCompiler(String lyCode) {

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
                image = new Image(Files.newInputStream(workingDir.resolve(outFile)));

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
}
