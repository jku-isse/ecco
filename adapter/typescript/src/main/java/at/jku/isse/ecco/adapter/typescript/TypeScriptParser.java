package at.jku.isse.ecco.adapter.typescript;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.utils.JavetOSUtils;
import com.caoccao.javet.values.V8Value;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class TypeScriptParser {
    static File codeFile;

    static {
        InputStream inputStream = TypeScriptParser.class.getClassLoader().getResourceAsStream("script/parse.js");
        if (inputStream == null) {
            System.out.println("Could not find resource");
        }
        //Resource stream to codeFile
        Path outputPath = Path.of("parse.js");
        try (OutputStream outputStream = new FileOutputStream(outputPath.toFile())) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            codeFile = outputPath.toFile();
        } catch (IOException e) {
            System.out.println("Error writing to file");
            e.printStackTrace();
        }
    }

    private static final String SCRIPT_PATH = "../adapter/typescript/src/main/resources/script/parse.js";
    private static final String NODE_MODULE_PATH = "../adapter/typescript/src/main/resources/script/node_modules/typescript";

    public HashMap<String, Object> parse(Path path) throws FileNotFoundException {
        HashMap<String, Object> res;
        var read = new BufferedReader(new FileReader(path.toFile()));
        Path cwd = Path.of(JavetOSUtils.WORKING_DIRECTORY);
        File codeFile = cwd.resolve(SCRIPT_PATH).toFile();
        var nodePath = cwd.resolve(NODE_MODULE_PATH).toString();
        var fileContent = read.lines().collect(Collectors.joining("\n"));
        try (NodeRuntime v8Runtime = V8Host.getNodeInstance().createV8Runtime()) {
            v8Runtime.getGlobalObject().set("fileContent", fileContent);
            v8Runtime.getGlobalObject().set("nodePath", nodePath);
            try (V8Value x = v8Runtime.getExecutor(codeFile).execute()) {
                res = v8Runtime.getExecutor("sf").executeObject();
            }
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
        return res;
    }
}
