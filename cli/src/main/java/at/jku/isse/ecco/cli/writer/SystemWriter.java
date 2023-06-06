package at.jku.isse.ecco.cli.writer;

public class SystemWriter implements OutWriter {
    @Override
    public void println(String line) {
        System.out.println(line);
    }
}
