package at.jku.isse.ecco.gui.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Directory {
    public static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> fileStream = Files.list(directory)) {
            List<Path> fileList = fileStream.collect(Collectors.toList());

            return fileList.isEmpty();
        }
    }
}
