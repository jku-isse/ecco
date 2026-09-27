package at.jku.isse.ecco.adapter.typescript;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.V8Value;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class TypeScriptParser {

    /**
     * parse.js (as source text) and the TypeScript compiler module (node_modules/typescript, as
     * real files that Node can load). Both come from the classpath: used in place when running from build
     * directories, extracted once to a temporary directory when running from a jar. This used to
     * write parse.js into the JVM's working directory and look for the compiler at
     * "<working directory>/../adapter/typescript/src/main/resources/script/node_modules/typescript",
     * which only existed when running one level below a source checkout's root (e.g.
     * `gradle :ecco-gui:run`) - not in the packaged app, the CLI or this module's own tests (see
     * TypeScriptReaderTest).
     */
    private static final class Scripts {
        static final String PARSE_JS_SOURCE;
        static final Path TYPESCRIPT_MODULE;

        static {
            try {
                Path scriptDir = locateScriptDirectory();
                PARSE_JS_SOURCE = Files.readString(scriptDir.resolve("parse.js"));
                TYPESCRIPT_MODULE = scriptDir.resolve("node_modules").resolve("typescript");
            } catch (IOException | URISyntaxException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private static Path locateScriptDirectory() throws IOException, URISyntaxException {
            URL parseJs = TypeScriptParser.class.getClassLoader().getResource("script/parse.js");
            if (parseJs == null)
                throw new FileNotFoundException("script/parse.js is not on the classpath");
            if ("file".equals(parseJs.getProtocol()))
                return Path.of(parseJs.toURI()).getParent();

            // running from a jar: Node needs real files
            Path tempDir = Files.createTempDirectory("ecco-typescript-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(tempDir)));
            try (FileSystem jar = FileSystems.newFileSystem(parseJs.toURI(), Map.of())) {
                Path source = jar.getPath("/script");
                try (Stream<Path> files = Files.walk(source)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String relative = source.relativize(file).toString();
                        if (!relative.isEmpty() && !relative.equals("parse.js") && !relative.startsWith("node_modules/typescript"))
                            continue;
                        Path target = tempDir.resolve(relative);
                        if (Files.isDirectory(file))
                            Files.createDirectories(target);
                        else
                            Files.copy(file, target);
                    }
                }
            }
            return tempDir;
        }

        private static void deleteRecursively(Path dir) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
            } catch (IOException ignored) {
            }
        }
    }

    public HashMap<String, Object> parse(Path path) throws FileNotFoundException {
        HashMap<String, Object> res;
        String fileContent;
        try {
            fileContent = Files.readString(path);
        } catch (IOException e) {
            FileNotFoundException notFound = new FileNotFoundException("Could not read " + path);
            notFound.initCause(e);
            throw notFound;
        }
        var nodePath = Scripts.TYPESCRIPT_MODULE.toString();
        try (NodeRuntime v8Runtime = V8Host.getNodeInstance().createV8Runtime()) {
            v8Runtime.getConverter().getConfig().setMaxDepth(1000);
            v8Runtime.getGlobalObject().set("fileContent", fileContent);
            v8Runtime.getGlobalObject().set("nodePath", nodePath);
            // executed as source text, not via getExecutor(File): executing a file changes the whole
            // process's native working directory to the script's folder, so every relative path in
            // this JVM (e.g. the CLI's "." repository) resolved against it after the first .ts file
            // was parsed (see TypeScriptReaderTest). parse.js require()s the compiler by absolute path.
            try (V8Value x = v8Runtime.getExecutor(Scripts.PARSE_JS_SOURCE).execute()) {
                res = v8Runtime.getExecutor("sf").executeObject();
            }
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
        return res;
    }
}
