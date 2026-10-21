package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.cli.command.fetch.FetchCommand;
import at.jku.isse.ecco.cli.command.pull.PullCommand;
import at.jku.isse.ecco.cli.command.push.PushCommand;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** IF-C4a: fetch, pull and push use the remote origin when given none; IF-C4b: pull, push and fork take --exclude. */
public class MainArgumentsTest {

    @Test
    public void fetchPullAndPushDefaultToOrigin() throws Exception {
        // the subcommands are registered by the first run - an invalid one, which only prints usage
        PrintStream err = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        try {
            Main.run(new String[]{"no-such-command"});
        } finally {
            System.setErr(err);
        }
        for (String[] command : new String[][]{{FetchCommand.FETCH, FetchCommand.REMOTE_KEY},
                {PullCommand.PULL, PullCommand.REMOTE_KEY}, {PushCommand.PUSH, PushCommand.REMOTE_KEY}}) {
            Namespace parsed = Main.parser.parseArgs(new String[]{command[0]});
            assertEquals(EccoService.ORIGIN_REMOTE_NAME, parsed.getString(command[1]), command[0]);
        }
    }

    @Test
    public void pullPushAndForkTakeRevisionsToExclude() throws Exception {
        PrintStream err = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        try {
            Main.run(new String[]{"no-such-command"});
        } finally {
            System.setErr(err);
        }
        assertEquals("A.1, B.2", Main.parser.parseArgs(new String[]{PullCommand.PULL, PullCommand.FLAG_EXCLUDE, "A.1, B.2"}).getString(PullCommand.EXCLUDE_KEY));
        assertEquals("A.1", Main.parser.parseArgs(new String[]{PushCommand.PUSH, "upstream", PushCommand.FLAG_EXCLUDE, "A.1"}).getString(PushCommand.EXCLUDE_KEY));
        assertEquals("A.1", Main.parser.parseArgs(new String[]{at.jku.isse.ecco.cli.command.fork.ForkCommand.FORK, "localhost:3770",
                at.jku.isse.ecco.cli.command.fork.ForkCommand.FLAG_EXCLUDE, "A.1"}).getString(at.jku.isse.ecco.cli.command.fork.ForkCommand.EXCLUDE_KEY));
    }
}
