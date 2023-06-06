package at.jku.isse.ecco.cli.init;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class InitArgumentsTest {
    @Test
    public void testArguments() {
        final Boolean[] result = new Boolean[]{false};

        ArgumentParser parser = ArgumentParsers
                .newFor("ecco")
                .build()
                .description("ECCO. A Variability-Aware / Feature-Oriented Version Control System.")
                .version("0.1.4");

        parser.addArgument("at.jku.isse.ecco.cli.init").action(new ArgumentAction() {
            @Override
            public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value) throws ArgumentParserException {
                result[0] = true;
            }

            @Override
            public void onAttach(Argument arg) {
            }

            @Override
            public boolean consumeArgument() {
                return true;
            }
        });

        try {
            System.out.println(parser.formatHelp());
            parser.parseArgs(new String[]{"at.jku.isse.ecco.cli.init"});
        } catch (ArgumentParserException e) {
            fail(e.getMessage());
        }

        assertTrue(result[0]);
    }
}
