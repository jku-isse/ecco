package at.jku.isse.ecco.cli.init;

import at.jku.isse.ecco.cli.DefaultArgumentAction;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.util.Map;
import java.util.function.Consumer;

public class InitAction extends DefaultArgumentAction {
    private final EccoService eccoService;

    public InitAction(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value, Consumer<Object> valueSetter) throws ArgumentParserException {
        this.eccoService.init();
    }
}
