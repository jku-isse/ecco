package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text file's final newline is part of the variant: recorded once per file, the newest commit's was
 * written into every variant - a README that lost its final newline in the last commit lost it in
 * every checkout. Now it is traced like a line (FinalNewlineArtifactData), for the text, Markdown
 * and C adapters alike.
 */
@Timeout(120)
public class FinalNewlineVariantsTest {

    @Test
    public void eachVariantEndsAsItWasCommitted(@TempDir Path tmp) throws Exception {
        List<String> endings = List.of("\n", "", "\n", "");
        Map<String, String> files = Map.of(
                "notes.txt", "one\ntwo",
                "README.md", "# Title\n\nSome text.",
                "main.c", "int main(void) {\n    return 0;\n}");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            for (int i = 0; i < endings.size(); i++) {
                Path dir = Files.createDirectories(tmp.resolve("v" + i));
                for (Map.Entry<String, String> file : files.entrySet())
                    Files.writeString(dir.resolve(file.getKey()), file.getValue() + endings.get(i));
                service.setBaseDir(dir);
                service.commit("v" + i, "BASE, V" + i);
            }
            for (int i = 0; i < endings.size(); i++) {
                Path out = Files.createDirectories(tmp.resolve("out" + i));
                service.setBaseDir(out);
                service.checkout("BASE, V" + i);
                for (Map.Entry<String, String> file : files.entrySet())
                    assertEquals(file.getValue() + endings.get(i), Files.readString(out.resolve(file.getKey())), "v" + i + " " + file.getKey());
            }
        }
    }
}
