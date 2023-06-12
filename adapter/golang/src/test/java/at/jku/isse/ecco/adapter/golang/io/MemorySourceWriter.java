package at.jku.isse.ecco.adapter.golang.io;

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MemorySourceWriter implements SourceWriter{
    private final Map<Path, String> writtenFiles = new HashMap<>();

    @Override
    public void writeString(Path path, CharSequence charSequence, OpenOption options) {
        this.writtenFiles.put(path, charSequence.toString());
    }

    public Map<Path, String> getWrittenFiles() {
        return Collections.unmodifiableMap(writtenFiles);
    }
}
