package at.jku.isse.ecco.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The server as an editor talks to it - JSON-RPC over a pair of streams: a hover names the features
 * of the line, the code lenses mark where they change, and an edit is reported as not committed.
 */
@Timeout(120)
public class EccoLanguageServerTest {

    /** Records what the server publishes. */
    static class Client implements EccoLanguageClient {
        final BlockingQueue<PublishDiagnosticsParams> diagnostics = new LinkedBlockingQueue<>();
        final BlockingQueue<FeatureRangesParams> featureRanges = new LinkedBlockingQueue<>();

        @Override
        public void featureRanges(FeatureRangesParams params) {
            this.featureRanges.add(params);
        }
        final BlockingQueue<MessageParams> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> progress = new LinkedBlockingQueue<>();

        @Override
        public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
            this.progress.add("create");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void notifyProgress(ProgressParams params) {
            WorkDoneProgressNotification value = (WorkDoneProgressNotification) params.getValue().getLeft();
            this.progress.add(value.getKind() + ": " + switch (value) {
                case WorkDoneProgressBegin begin -> begin.getMessage();
                case WorkDoneProgressReport report -> report.getMessage();
                case WorkDoneProgressEnd end -> end.getMessage();
                default -> "";
            });
        }

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams params) {
            this.diagnostics.add(params);
        }

        @Override
        public void showMessage(MessageParams params) {
            this.messages.add(params);
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams params) {
        }
    }

    /** A blank line inside a class belongs to the class: the lens goes to the next line with code. */
    @Test
    public void aCodeLensIsNotOnABlankLine() {
        String text = "class C {\n    int a;\n\n    int b() {\n    }\n}\n";
        FeatureLens.FileTrace trace = new FeatureLens.FileTrace("X", "X", List.of(
                new FeatureLens.Span(1, 6, "X", 1),
                new FeatureLens.Span(2, 2, "A", 2),
                new FeatureLens.Span(4, 5, "X", 2)), List.of(), null);
        assertEquals(List.of("0 ECCO: X", "1 ECCO: A", "3 ECCO: X"), EccoLanguageServer.codeLenses(trace, text).stream()
                .map(l -> l.getRange().getStart().getLine() + " " + l.getCommand().getTitle()).toList());
    }

    /**
     * Runs by condition, but the file's own; blank lines inside a run are part of it, those between
     * two runs part of neither; code not committed is its own run, without a palette slot.
     */
    @Test
    public void featureRangesAreRunsOfOneConditionButTheFilesOwn() {
        String text = "class C {\n    int a;\n\n    int a2;\n\n    int n;\n    int b() {\n    }\n}\n";
        FeatureLens.FileTrace trace = new FeatureLens.FileTrace("X", "X", List.of(
                new FeatureLens.Span(1, 9, "X", 1),
                new FeatureLens.Span(2, 2, "A", 2),
                new FeatureLens.Span(4, 4, "A", 2),
                new FeatureLens.Span(6, 6, null, 2),
                new FeatureLens.Span(7, 8, "B", 2)), List.of(), null);
        java.util.Map<String, Integer> slots = new java.util.HashMap<>();
        assertEquals(List.of(
                        new EccoLanguageClient.FeatureRange(1, 3, "A", 0),
                        new EccoLanguageClient.FeatureRange(5, 5, null, -1),
                        new EccoLanguageClient.FeatureRange(6, 7, "B", 1)),
                EccoLanguageServer.featureRanges(trace, text, c -> slots.computeIfAbsent(c, k -> slots.size())));
    }

    /** A run of one condition over several lines folds, showing its condition; one line, or the file's own condition, not. */
    @Test
    public void foldingRangesAreTheRunsOfSeveralLines() {
        String text = "x\na1\na2\n\nb1\nnew1\nnew2\n";
        FeatureLens.FileTrace trace = new FeatureLens.FileTrace("X", "X", List.of(
                new FeatureLens.Span(1, 1, "X", 1),
                new FeatureLens.Span(2, 3, "A", 1),
                new FeatureLens.Span(5, 5, "B", 1),
                new FeatureLens.Span(6, 7, null, 1)), List.of(), null);
        assertEquals(List.of("1-2 ECCO: A", "5-6 ECCO: not committed"), EccoLanguageServer.foldingRanges(trace, text).stream()
                .map(r -> r.getStartLine() + "-" + r.getEndLine() + " " + r.getCollapsedText()).toList());
    }

    @Test
    public void hoverCodeLensAndDiagnosticsOverTheProtocol(@TempDir Path tmp) throws Exception {
        Path work = FeatureLensTest.checkout(tmp, "BASE, A, B");
        PipedInputStream serverIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream(serverIn);
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream serverOut = new PipedOutputStream(clientIn);

        EccoLanguageServer server = new EccoLanguageServer();
        Main.launcher(server, serverIn, serverOut).startListening();
        Client client = new Client();
        Launcher<LanguageServer> clientLauncher = LSPLauncher.createClientLauncher(client, clientIn, clientOut);
        clientLauncher.startListening();
        LanguageServer remote = clientLauncher.getRemoteProxy();

        InitializeParams init = new InitializeParams();
        init.setWorkspaceFolders(List.of(new WorkspaceFolder(work.toUri().toString(), "work")));
        ClientCapabilities capabilities = new ClientCapabilities();
        WindowClientCapabilities window = new WindowClientCapabilities();
        window.setWorkDoneProgress(true);
        capabilities.setWindow(window);
        init.setCapabilities(capabilities);
        InitializeResult result = remote.initialize(init).get(30, TimeUnit.SECONDS);
        assertTrue(result.getCapabilities().getHoverProvider().getLeft());
        remote.initialized(new InitializedParams());
        // the working directory of the workspace folder is prepared before a file is opened, shown as progress
        assertEquals("create", client.progress.poll(60, TimeUnit.SECONDS));
        assertEquals("begin: Loading the repository of work…", client.progress.poll(60, TimeUnit.SECONDS));
        String end = client.progress.poll(60, TimeUnit.SECONDS);
        assertTrue(end != null && end.matches("end: Ready in \\d+\\.\\d s"), end);

        String uri = work.resolve("notes.txt").toUri().toString();
        String text = Files.readString(work.resolve("notes.txt"));
        remote.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "plaintext", 1, text)));

        Hover hover = remote.getTextDocumentService().hover(new HoverParams(new TextDocumentIdentifier(uri), new Position(3, 0))).get(60, TimeUnit.SECONDS);
        assertTrue(hover.getContents().getRight().getValue().contains("`B`"), hover.getContents().getRight().getValue());

        List<? extends CodeLens> lenses = remote.getTextDocumentService().codeLens(new CodeLensParams(new TextDocumentIdentifier(uri))).get(60, TimeUnit.SECONDS);
        assertEquals(List.of("0 ECCO: BASE", "1 ECCO: A", "2 ECCO: BASE", "3 ECCO: B"),
                lenses.stream().map(l -> l.getRange().getStart().getLine() + " " + l.getCommand().getTitle()).toList());

        PublishDiagnosticsParams opened = client.diagnostics.poll(60, TimeUnit.SECONDS);
        assertNotNull(opened);
        assertTrue(opened.getDiagnostics().isEmpty(), opened.getDiagnostics().toString());
        // a1 (A) and b1 (B) coloured, each its own slot; the base lines have the file's condition
        EccoLanguageClient.FeatureRangesParams colours = client.featureRanges.poll(60, TimeUnit.SECONDS);
        assertNotNull(colours);
        assertEquals("BASE", colours.fileCondition());
        assertTrue(colours.configuration().matches("A\\.\\w+, B\\.\\w+, BASE\\.\\w+"), colours.configuration());
        assertEquals(List.of(new EccoLanguageClient.FeatureRange(1, 1, "A", 0), new EccoLanguageClient.FeatureRange(3, 3, "B", 1)), colours.ranges());

        // the lines of A and B are one line each: nothing to fold; the hover tells the commits too (b1: v2 and v3)
        assertEquals(List.of(), remote.getTextDocumentService().foldingRange(new FoldingRangeRequestParams(new TextDocumentIdentifier(uri))).get(60, TimeUnit.SECONDS));
        assertTrue(hover.getContents().getRight().getValue().contains("2 commits, the last"), hover.getContents().getRight().getValue());

        // an edit, not saved: line 2 is new
        VersionedTextDocumentIdentifier version = new VersionedTextDocumentIdentifier(uri, 2);
        remote.getTextDocumentService().didChange(new DidChangeTextDocumentParams(version,
                List.of(new TextDocumentContentChangeEvent("base1\nnew\na1\nbase2\nb1\n"))));
        PublishDiagnosticsParams edited = client.diagnostics.poll(60, TimeUnit.SECONDS);
        assertNotNull(edited);
        assertEquals(1, edited.getDiagnostics().size(), edited.getDiagnostics().toString());
        Diagnostic notCommitted = edited.getDiagnostics().getFirst();
        assertEquals(1, notCommitted.getRange().getStart().getLine());
        assertEquals(DiagnosticSeverity.Hint, notCommitted.getSeverity());

        // a file outside any working directory: nothing
        Path elsewhere = Files.writeString(tmp.resolve("elsewhere.txt"), "x\n");
        String other = elsewhere.toUri().toString();
        remote.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(other, "plaintext", 1, "x\n")));
        assertNull(remote.getTextDocumentService().hover(new HoverParams(new TextDocumentIdentifier(other), new Position(0, 0))).get(60, TimeUnit.SECONDS));
        assertTrue(client.messages.isEmpty(), client.messages.toString());

        // the editor's commands: the working directory found from a file of it, the features to suggest
        com.google.gson.JsonObject arguments = new com.google.gson.JsonObject();
        arguments.addProperty("uri", uri);
        Object info = remote.getWorkspaceService().executeCommand(new ExecuteCommandParams(EditorCommands.INFO, List.of(arguments))).get(60, TimeUnit.SECONDS);
        assertTrue(info.toString().contains("features=[A, B, BASE]"), info.toString());
        assertTrue(result.getCapabilities().getExecuteCommandProvider().getCommands().containsAll(EditorCommands.ALL));

        remote.shutdown().get(60, TimeUnit.SECONDS);
    }
}
