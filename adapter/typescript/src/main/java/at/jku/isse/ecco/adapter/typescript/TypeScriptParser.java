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

    private static String SCRIPT_PATH = "../adapter/typescript/src/main/resources/script/parse.js";

    public HashMap<String,Object> parse(Path path) throws FileNotFoundException {
        HashMap<String,Object> res;
        var read  = new BufferedReader(new FileReader(path.toFile()));
        File codeFile = Path.of(JavetOSUtils.WORKING_DIRECTORY)
                .resolve(SCRIPT_PATH).toFile();
        var fileContent = read.lines().collect(Collectors.joining("\n"));
        try (NodeRuntime v8Runtime = V8Host.getNodeInstance().createV8Runtime()) {
            v8Runtime.getGlobalObject().set("t",fileContent);
            V8Value x = v8Runtime.getExecutor(codeFile).execute();
            res = v8Runtime.getExecutor("sf").executeObject();
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
        return res;
    }
}
