package at.jku.isse.ecco.cli.writer;

import java.util.LinkedList;
import java.util.List;

public class StringWriter implements OutWriter {
    private final List<String> lines = new LinkedList<>();

    @Override
    public void println(String line) {
        this.lines.add(line);
    }

    public List<String> getLines() {
        return lines;
    }
}
