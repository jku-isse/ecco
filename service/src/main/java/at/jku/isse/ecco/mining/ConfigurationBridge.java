package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the configurations of committed variants into the plain
 * {@code List<Set<String>>} token form that {@link ConstraintMiner} operates
 * on. This is the only ECCO-coupled piece of the constraint-mining feature.
 *
 * <p>Tokens are feature-level (a {@link FeatureRevision}'s feature name, not
 * the revision), so different revisions of the same feature don't get
 * reported as mutually exclusive. Every entry of a {@link Configuration} is
 * already a positive selection -- everything else is implicitly negative at
 * the repository level -- so no sign check is needed.
 *
 * <p>Caller is responsible for {@link EccoService#open()}/{@code close()}.
 */
public final class ConfigurationBridge {

    private ConfigurationBridge() {
    }

    public static List<Set<String>> readConfigurations(EccoService service) {
        List<Set<String>> configs = new ArrayList<>();
        for (Commit commit : service.getCommits()) {
            configs.add(tokensOf(commit.getConfiguration()));
        }
        return configs;
    }

    /**
     * Feature-level tokens (feature name, not revision) selected by the given configuration.
     * Every entry is already a positive selection -- everything else is implicitly negative
     * at the repository level -- so no sign check is needed.
     */
    public static Set<String> tokensOf(Configuration cfg) {
        Set<String> tokens = new HashSet<>();
        for (FeatureRevision fr : cfg.getFeatureRevisions())
            tokens.add(fr.getFeature().getName());
        return tokens;
    }
}
