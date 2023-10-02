package at.jku.isse.ecco.adapter.golang.io;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public interface SourceWriter {
    void writeString(Path path, CharSequence charSequence, OpenOption options) throws IOException;
}
