package at.jku.isse.ecco.cli.features;

import at.jku.isse.ecco.cli.DefaultArgumentAction;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

public class ListAction extends DefaultArgumentAction {
    private final EccoService eccoService;
    private final OutWriter writer;

    public ListAction(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    @Override
    public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value, Consumer<Object> valueSetter) {
        eccoService.open();

        Collection<? extends Feature> features = this.eccoService.getRepository().getFeatures();

        for (Feature feature : features) {
            writer.println(feature.toString());
        }

        eccoService.close();
    }
}
