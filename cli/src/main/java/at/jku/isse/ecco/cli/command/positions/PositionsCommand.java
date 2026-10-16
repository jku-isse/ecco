package at.jku.isse.ecco.cli.command.positions;

import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import net.sourceforge.argparse4j.inf.Namespace;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prints the positions (classes, methods, sections, ...) each changed file of Git commits has
 * changes in, as this repository's adapters name them - what the Git import's feature suggestion
 * locates a commit's changes by (GitHistoryReader#getChangedPositions). One JSON object per commit:
 * {"commit": "...", "files": {"path": {"added": n, "removed": n, "positions": ["position", ...]}, ...}}. For replaying the suggestions
 * outside the GUI (experiments/llm-feature-suggestion).
 */
public class PositionsCommand implements Command {
    public static final String POSITIONS = "positions";
    public static final String CLONE_KEY = "clone";
    public static final String COMMITS_KEY = "commits";

    private final EccoService eccoService;
    private final OutWriter writer;

    public PositionsCommand(EccoService eccoService, OutWriter writer) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public PositionsCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        Object startDir = namespace.get(ProgramConstants.START_DIR);
        Path clone = (startDir instanceof Path path ? path : Path.of("")).resolve(namespace.getString(CLONE_KEY)).normalize();
        List<String> commits = namespace.getList(COMMITS_KEY);
        GitHistoryReader reader = new GitHistoryReader();
        Command.withOpenRepository(eccoService, () -> {
            for (String commit : commits)
                writer.println(json(commit, reader.getChangedPositions(clone, commit, eccoService.getReader())));
        });
    }

    static String json(String commit, Map<String, GitHistoryReader.ChangedFile> files) {
        return "{\"commit\":" + quote(commit) + ",\"files\":{" + files.values().stream()
                .map(f -> quote(f.path()) + ":{\"added\":" + f.added() + ",\"removed\":" + f.removed() + ",\"positions\":["
                        + f.positions().stream().map(PositionsCommand::quote).collect(Collectors.joining(",")) + "]}")
                .collect(Collectors.joining(",")) + "}}";
    }

    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20)
                        quoted.append(String.format("\\u%04x", (int) c));
                    else
                        quoted.append(c);
                }
            }
        }
        return quoted.append('"').toString();
    }
}
