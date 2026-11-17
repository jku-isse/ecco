package at.jku.isse.ecco.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;

import java.io.InputStream;
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
        launcher(server, System.in, protocol).startListening().get();
    }

    /** The server's end of the protocol on [in] and [out], connected to its client - which may colour code by feature. */
    static Launcher<EccoLanguageClient> launcher(EccoLanguageServer server, InputStream in, OutputStream out) {
        Launcher<EccoLanguageClient> launcher = new LSPLauncher.Builder<EccoLanguageClient>()
                .setLocalService(server).setRemoteInterface(EccoLanguageClient.class)
                .setInput(in).setOutput(out).create();
        server.connect(launcher.getRemoteProxy());
        return launcher;
    }
}
