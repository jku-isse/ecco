package at.jku.isse.ecco.adapter.typescript;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.utils.JavetOSUtils;
import com.caoccao.javet.values.V8Value;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class TypeScriptParser {

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
