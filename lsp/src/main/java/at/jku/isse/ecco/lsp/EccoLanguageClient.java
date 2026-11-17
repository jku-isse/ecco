package at.jku.isse.ecco.lsp;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.List;

/**
 * A language client that colours code by feature: the server tells it, after each trace of a file,
 * which lines have which condition ({@code ecco/featureRanges}). A client without it ignores the
 * notification - the hover, code lenses and diagnostics are plain LSP.
 */
public interface EccoLanguageClient extends LanguageClient {

    String FEATURE_RANGES = "ecco/featureRanges";

    /**
     * Lines 0-based and inclusive. {@code colour} is a slot of the shared categorical palette (the GUI's
     * CategoricalColorPalette): the same condition has the same slot in every file of a working
     * directory while the server runs; -1 for code not committed. Lines with the file's own condition
     * are not listed: present whenever the file is, they are not what varies within it.
     */
    record FeatureRange(int startLine, int endLine, String condition, int colour) {
    }

    record FeatureRangesParams(String uri, String fileCondition, List<FeatureRange> ranges) {
    }

    @JsonNotification(FEATURE_RANGES)
    void featureRanges(FeatureRangesParams params);
}
