package at.jku.isse.ecco.cli.command.features;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.Collection;

public class ListFeaturesCommand implements Command {
    public final static String FEATURES = "features";
    public static final String NAME_KEY = "name";
    private final EccoService eccoService;
    private final OutWriter writer;

    public ListFeaturesCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public ListFeaturesCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        String name = namespace.getString(NAME_KEY);

        eccoService.open();

        Collection<? extends Feature> features = this.eccoService.getRepository().getFeatures();

        // each with its id as a configuration takes it: two features of one name can only be told
        // apart by it ("Feature name is not unique. Use feature id instead."), and nothing showed it
        for (Feature feature : features) {
            if (name == null) {
                writer.println(feature + " [" + feature.getId() + "]");
            } else if (feature.getName().equals(name)) {
                writer.println(feature + " [" + feature.getId() + "]");
                for (FeatureRevision featureRevision : feature.getRevisions()) {
                    writer.println("\t" + featureRevision);
                }
            }
        }

        eccoService.close();
    }
}
