package at.jku.isse.ecco.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Starts the ECCO language server on standard input and output, as editors start language servers.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.setProperty("ecco.headless", "true");
        // standard output carries the protocol: anything else printed there would break it
        OutputStream protocol = System.out;
        System.setOut(new PrintStream(System.err, true));
        EccoLanguageServer server = new EccoLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, protocol);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
