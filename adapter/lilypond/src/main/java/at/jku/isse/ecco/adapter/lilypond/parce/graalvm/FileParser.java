package at.jku.isse.ecco.adapter.lilypond.parce.graalvm;

import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondParser;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileParser implements LilypondParser<ParceToken> {
    protected static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());
    private Context context;

    public void init() throws IOException {
        String VENV_EXECUTABLE;
        try {
            URI propUri = Objects.requireNonNull(ClassLoader.getSystemResource("graalVM-config.properties")).toURI();
            Properties props = new Properties();
            props.load(Files.newInputStream(Path.of(propUri), StandardOpenOption.READ));
            VENV_EXECUTABLE = props.getProperty("graalvm_python_venv");

        } catch (URISyntaxException e) {
            throw new IOException("could not load properties file", e);
        }

        try {
            context = Context.newBuilder("python")
                    .allowIO(true)
                    .allowExperimentalOptions(true)
                    .allowHostAccess(HostAccess.SCOPED)
                    .option("python.Executable", VENV_EXECUTABLE)
                    .option("python.ForceImportSite", "true")
                    .build();
            context.eval("python", "import sys;import parce;" +
                    "from parce.lang.lilypond import LilyPond;");
        } catch (IllegalArgumentException e) {
            throw new IOException("could not build python environment", e);
        }
    }

    public LilypondNode<ParceToken> parse(Path path) {
        return parse(path, null);
    }

    public LilypondNode<ParceToken> parse(Path path, HashMap<String, Integer> tokenMetric) {
        LOGGER.log(Level.INFO, "start parsing {0}", path);

        LilypondService service = new LilypondService();
        context.getBindings("python").putMember("service", service);

        context.eval("python", "f = open('" + path.toString() + "', 'r', -1, 'UTF-8');" +
                "s = f.read();" +
                "f.close();" +
                "lastPos = 0;");

        /* TODO: the \ in lilypond code raises an exception in parce module (problem of GraalVM)
         - if it is fixed, remove two lines below */
        context.eval("python", "for e in parce.events(LilyPond.root, 'g4 \\mf'):\n" + // contains '\' to test if parsing works
                "    print(e)");

        context.eval("python", """
                for e in parce.events(LilyPond.root, s):
                    pop = 0
                    if e.target:
                        service.popContext(e.target.pop)
                        first = e.lexemes[0]
                        if first[0] > lastPos:
                            service.addWhitespace(lastPos, s[lastPos:first[0]])
                            lastPos = first[0] + len(first[1])
                        for c in e.target.push:
                            service.pushContext(c.fullname)
                    for tpl in e.lexemes:
                        if tpl[0] > lastPos:
                            service.addWhitespace(lastPos, s[lastPos:tpl[0]])
                        service.addToken(tpl[0], tpl[1], str(tpl[2]))
                        lastPos = tpl[0] + len(tpl[1])
                service.addWhitespace(lastPos, s[lastPos:])
                """);

        return service.getRoot();
    }

    public void shutdown() {
        context.close();
    }

}
