package at.jku.isse.ecco.adapter.python.repositoryTest;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class PythonAdapterRepositoryTestLogger extends ArrayList<ArrayList<String>> {

    private final ArrayList<ArrayList<String>> internal;

    public PythonAdapterRepositoryTestLogger() {
        internal = new ArrayList<>();
    }

    public void add(String... entries) {
        ArrayList<String> line = new ArrayList<>();
        if (internal.size() == 0) {
            line.add("Commit");
        } else {
            line.add(String.valueOf(internal.size()));
        }
        internal.add(line);
        line.addAll(Arrays.asList(entries));
    }

    public void add(int nCommit, String... entries) {
        if (nCommit >= internal.size()) {
            add(entries);
        } else {
            for (String e : entries) {
                internal.get(nCommit).add(e);
            }
        }
    }

    public void createCSV(Path repoPath, String fileName) {
        File csvOutputFile = new File(repoPath.resolve(fileName + ".csv").toAbsolutePath().toString());
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            internal.stream()
                    .map(this::convertToCSV)
                    .forEach(pw::println);
        } catch (Exception ignored) {
        }
    }

    private String convertToCSV(ArrayList<String> data) {
        return data.stream()
                .map(this::escapeSpecialCharacters)
                .collect(Collectors.joining(";"));
    }

    private String escapeSpecialCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(";") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }
}
