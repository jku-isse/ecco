package at.jku.isse.ecco.cli.writer;

public interface OutWriter {
    void println(String line);

    void printf(String formatString, Object... args);
}
