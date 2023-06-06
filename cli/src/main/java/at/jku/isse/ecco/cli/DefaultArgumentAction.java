package at.jku.isse.ecco.cli;

import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.util.Map;
import java.util.function.Consumer;

public class DefaultArgumentAction implements ArgumentAction {
    @Override
    public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value) throws ArgumentParserException {}

    @Override
    public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value, Consumer<Object> valueSetter) throws ArgumentParserException {
        ArgumentAction.super.run(parser, arg, attrs, flag, value, valueSetter);
    }

    @Override
    public void onAttach(Argument arg) {}

    @Override
    public boolean consumeArgument() {
        return true;
    }
}
