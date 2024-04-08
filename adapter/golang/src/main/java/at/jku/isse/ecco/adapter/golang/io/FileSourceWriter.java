package at.jku.isse.ecco.adapter.golang.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class FileSourceWriter implements SourceWriter {
    @Override
    public void writeString(Path path, CharSequence charSequence, OpenOption options) throws IOException {
        Files.writeString(path, charSequence, options);
    }
}
