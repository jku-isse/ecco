package at.jku.isse.ecco.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ECCO language server: for files of a working directory holding a checkout, a hover says which
 * features a line is traced to, a code lens marks where that changes, and diagnostics show the
 * checkout's ORDER warnings and code not committed yet. Read-only: it never writes the repository
 * or the working directory, and holds no repository lock between requests (each read is a
 * transaction of its own), so the command line and the GUI keep working beside it.
 * <p>
 * All ECCO work runs on one thread; an edit is traced again after a pause in typing.
 */
public class EccoLanguageServer implements LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService {

    private static final Logger LOGGER = Logger.getLogger(EccoLanguageServer.class.getName());
    static final String SOURCE = "ECCO";
    /** How long typing must pause before an edit is traced again. */
    static final long DEBOUNCE_MS = 400;

    private final ScheduledExecutorService ecco = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ecco-lsp");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, FeatureLens.FileTrace> traces = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    /** One per working directory (ecco thread only); a failed open is remembered as its message. */
    private final Map<Path, FeatureLens> lenses = new HashMap<>();
    private final Map<Path, String> failedLenses = new HashMap<>();

    private LanguageClient client;
    /** The palette slot of each condition, in the order they were first seen (ecco thread only). */
    private final Map<String, Integer> colours = new HashMap<>();
    private boolean codeLensRefresh;

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ClientCapabilities capabilities = params.getCapabilities();
        this.codeLensRefresh = capabilities != null && capabilities.getWorkspace() != null && capabilities.getWorkspace().getCodeLens() != null
                && Boolean.TRUE.equals(capabilities.getWorkspace().getCodeLens().getRefreshSupport());
        ServerCapabilities server = new ServerCapabilities();
        TextDocumentSyncOptions sync = new TextDocumentSyncOptions();
        sync.setOpenClose(true);
        sync.setChange(TextDocumentSyncKind.Full);
        sync.setSave(new SaveOptions(false));
        server.setTextDocumentSync(sync);
        server.setHoverProvider(true);
        server.setCodeLensProvider(new CodeLensOptions(false));
        return CompletableFuture.completedFuture(new InitializeResult(server, new ServerInfo("ecco-lsp")));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.supplyAsync(() -> {
            this.lenses.values().forEach(FeatureLens::close);
            this.lenses.clear();
            return null;
        }, this.ecco);
    }

    @Override
    public void exit() {
        this.ecco.shutdownNow();
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return this;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return this;
    }

    // --- documents

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        this.documents.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
        this.schedule(params.getTextDocument().getUri(), 0);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (changes.isEmpty())
            return;
        // full sync: the last change is the whole text
        this.documents.put(params.getTextDocument().getUri(), changes.getLast().getText());
        this.schedule(params.getTextDocument().getUri(), DEBOUNCE_MS);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // the checkout may have changed with it (.config) - traced again in any case
        this.schedule(params.getTextDocument().getUri(), 0);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        this.documents.remove(uri);
        this.traces.remove(uri);
        Optional.ofNullable(this.pending.remove(uri)).ifPresent(f -> f.cancel(false));
        if (this.client != null)
            this.client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // a checkout or commit elsewhere: trace the open documents again
        this.documents.keySet().forEach(uri -> this.schedule(uri, 0));
    }

    private void schedule(String uri, long delayMs) {
        ScheduledFuture<?> next = this.ecco.schedule(() -> this.analyze(uri), delayMs, TimeUnit.MILLISECONDS);
        Optional.ofNullable(this.pending.put(uri, next)).ifPresent(f -> f.cancel(false));
    }

    /** On the ecco thread. */
    private void analyze(String uri) {
        String content = this.documents.get(uri);
        Path file = path(uri);
        if (content == null || file == null)
            return;
        FeatureLens.FileTrace trace;
        try {
            FeatureLens lens = this.lens(file);
            if (lens == null)
                return; // not in a working directory of ECCO, or it could not be opened (said once)
            trace = lens.trace(file, content);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not trace " + uri, e);
            trace = FeatureLens.FileTrace.problem(null, "Could not trace the file: " + e);
        }
        this.traces.put(uri, trace);
        if (this.client != null) {
            this.client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics(trace)));
            if (this.client instanceof EccoLanguageClient ecco)
                ecco.featureRanges(new EccoLanguageClient.FeatureRangesParams(uri, trace.fileCondition(),
                        featureRanges(trace, content, c -> this.colours.computeIfAbsent(c, k -> this.colours.size()))));
            if (this.codeLensRefresh)
                this.client.refreshCodeLenses();
        }
    }

    private FeatureLens lens(Path file) {
        Path dir = FeatureLens.findWorkingDir(file);
        if (dir == null || this.failedLenses.containsKey(dir))
            return null;
        FeatureLens lens = this.lenses.get(dir);
        if (lens == null) {
            try {
                lens = new FeatureLens(dir);
                this.lenses.put(dir, lens);
            } catch (Exception e) {
                String message = "ECCO: could not open the repository of " + dir + ": " + e.getMessage();
                LOGGER.log(Level.WARNING, message, e);
                this.failedLenses.put(dir, message);
                if (this.client != null)
                    this.client.showMessage(new MessageParams(MessageType.Warning, message));
                return null;
            }
        }
        return lens;
    }

    // --- what the editor shows

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        int line = params.getPosition().getLine() + 1;
        return CompletableFuture.supplyAsync(() -> {
            FeatureLens.FileTrace trace = this.traces.get(uri);
            String text = hoverText(trace, line);
            return text == null ? null : new Hover(new MarkupContent(MarkupKind.MARKDOWN, text));
        }, this.ecco);
    }

    static String hoverText(FeatureLens.FileTrace trace, int line) {
        if (trace == null)
            return null;
        StringBuilder text = new StringBuilder("**ECCO** · ");
        FeatureLens.Span span = trace.at(line);
        if (span != null && span.committed())
            text.append("`").append(span.condition()).append("`");
        else if (span != null)
            text.append("not committed - new or changed since the checkout");
        else if (trace.fileCondition() != null)
            text.append("file: `").append(trace.fileCondition()).append("`");
        else if (trace.problem() != null)
            return text.append(trace.problem()).toString();
        else
            return null;
        if (trace.problem() != null)
            text.append("\n\n").append(trace.problem());
        if (trace.configuration() != null)
            text.append("\n\nchecked out: `").append(trace.configuration()).append("`");
        return text.toString();
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        String uri = params.getTextDocument().getUri();
        return CompletableFuture.supplyAsync(() -> codeLenses(this.traces.get(uri), this.documents.getOrDefault(uri, "")), this.ecco);
    }

    /**
     * A lens where the traced condition changes, going down the file [text]; one at the top for a file
     * traced as a whole. Blank lines are skipped: inside a class they belong to the class, and a lens
     * there stood above nothing.
     */
    static List<CodeLens> codeLenses(FeatureLens.FileTrace trace, String text) {
        List<CodeLens> lenses = new ArrayList<>();
        if (trace == null)
            return lenses;
        if (trace.spans().isEmpty()) {
            String title = trace.fileCondition() != null ? "ECCO: " + trace.fileCondition() : trace.problem() != null ? "ECCO: " + firstSentence(trace.problem()) : null;
            if (title != null)
                lenses.add(lens(1, title));
            return lenses;
        }
        List<String> lines = text.lines().toList();
        int last = trace.spans().stream().mapToInt(FeatureLens.Span::endLine).max().orElse(0);
        String previous = null;
        for (int line = 1; line <= last; line++) {
            FeatureLens.Span span = trace.at(line);
            if (span == null || line <= lines.size() && lines.get(line - 1).isBlank())
                continue; // lines no node is on, and blank ones, keep the lens before them
            String label = span.committed() ? span.condition() : "not committed";
            if (!label.equals(previous))
                lenses.add(lens(line, "ECCO: " + label));
            previous = label;
        }
        return lenses;
    }

    private static CodeLens lens(int line, String title) {
        Range range = new Range(new Position(line - 1, 0), new Position(line - 1, 0));
        return new CodeLens(range, new Command(title, ""), null);
    }

    private static String firstSentence(String text) {
        int end = text.indexOf(": ");
        return end > 0 && end < 80 ? text.substring(0, end) : text;
    }

    /**
     * The runs of lines with one condition, going down the file [text] - by the innermost node of each
     * line, as the code lenses - but those with the file's own condition. A blank line, or one no node
     * is on, joins a run when the next line with code continues it.
     */
    static List<EccoLanguageClient.FeatureRange> featureRanges(FeatureLens.FileTrace trace, String text, ToIntFunction<String> colourOf) {
        List<EccoLanguageClient.FeatureRange> ranges = new ArrayList<>();
        List<String> lines = text.lines().toList();
        int last = trace.spans().stream().mapToInt(FeatureLens.Span::endLine).max().orElse(0);
        String current = null; // the run's label: its condition, or "" for code not committed
        int start = -1, end = -1;
        for (int line = 1; line <= last + 1; line++) {
            FeatureLens.Span span = line <= last ? trace.at(line) : null;
            boolean code = span != null && !(line <= lines.size() && lines.get(line - 1).isBlank());
            if (line <= last && !code)
                continue;
            String label = span == null ? null : span.committed() ? span.condition() : "";
            if (current != null && !current.equals(label)) {
                if (!current.equals(trace.fileCondition()))
                    ranges.add(new EccoLanguageClient.FeatureRange(start - 1, end - 1, current.isEmpty() ? null : current,
                            current.isEmpty() ? -1 : colourOf.applyAsInt(current)));
                current = null;
            }
            if (label == null)
                continue;
            if (current == null) {
                current = label;
                start = line;
            }
            end = line;
        }
        return ranges;
    }

    static List<Diagnostic> diagnostics(FeatureLens.FileTrace trace) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (FeatureLens.OrderWarning warning : trace.orderWarnings())
            diagnostics.add(diagnostic(warning.startLine(), warning.endLine(), warning.message(), DiagnosticSeverity.Warning));
        // code not committed, as runs of lines
        BitSet uncommitted = new BitSet();
        for (FeatureLens.Span span : trace.spans())
            if (!span.committed())
                uncommitted.set(span.startLine(), span.endLine() + 1);
        for (int start = uncommitted.nextSetBit(0); start >= 0; start = uncommitted.nextSetBit(start)) {
            int end = uncommitted.nextClearBit(start) - 1;
            diagnostics.add(diagnostic(start, end, "Not committed: new or changed since the checkout of " + trace.configuration() + ".", DiagnosticSeverity.Hint));
            start = end + 1;
        }
        return diagnostics;
    }

    private static Diagnostic diagnostic(int startLine, int endLine, String message, DiagnosticSeverity severity) {
        Range range = new Range(new Position(startLine - 1, 0), new Position(endLine, 0));
        return new Diagnostic(range, message, severity, SOURCE);
    }

    static Path path(String uri) {
        try {
            URI parsed = URI.create(uri);
            return "file".equals(parsed.getScheme()) ? Path.of(parsed) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
