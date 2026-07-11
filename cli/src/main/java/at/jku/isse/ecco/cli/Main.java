package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.cli.command.adapters.ListAdaptersCommand;
import at.jku.isse.ecco.cli.command.checkout.CheckoutCommand;
import at.jku.isse.ecco.cli.command.commit.CommitCommand;
import at.jku.isse.ecco.cli.command.dependencygraph.DependencyGraphCommand;
import at.jku.isse.ecco.cli.command.features.ListFeaturesCommand;
import at.jku.isse.ecco.cli.command.fetch.FetchCommand;
import at.jku.isse.ecco.cli.command.fork.ForkCommand;
import at.jku.isse.ecco.cli.command.init.InitCommand;
import at.jku.isse.ecco.cli.command.minimizepreview.MinimizePreviewCommand;
import at.jku.isse.ecco.cli.command.property.GetCommand;
import at.jku.isse.ecco.cli.command.property.SetCommand;
import at.jku.isse.ecco.cli.command.pull.PullCommand;
import at.jku.isse.ecco.cli.command.push.PushCommand;
import at.jku.isse.ecco.cli.command.remotes.RemotesCommand;
import at.jku.isse.ecco.cli.command.status.StatusCommand;
import at.jku.isse.ecco.cli.command.suggestconstraints.SuggestConstraintsCommand;
import at.jku.isse.ecco.cli.command.traces.TracesCommand;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;

import java.nio.file.Path;

/**
 * Main class for the CLI. Parses the command line parameters.
 */
public class Main {

    public static final ArgumentParser parser = ArgumentParsers.newFor(ProgramConstants.ECCO).build();
    private static final CommandRegister commandRegister = new CommandRegister();
    private static final EccoService eccoService = new EccoService(Path.of("."));

    public static void main(String[] args) {
        registerCommands();

        try {
            Namespace namespace = parser.parseArgs(args);
            String command = namespace.getString("command");
            commandRegister.run(command, namespace);
        } catch (ArgumentParserException e) {
            parser.printHelp();
        }
    }

    private static void registerCommands() {
        Subparsers commandParser = parser.addSubparsers().title(ProgramConstants.COMMAND);

        registerSimpleCommand(commandParser, InitCommand.INIT, new InitCommand(eccoService));
        registerSimpleCommand(commandParser, ListAdaptersCommand.ADAPTERS, new ListAdaptersCommand(eccoService));
        registerSimpleCommand(commandParser, StatusCommand.STATUS, new StatusCommand(eccoService));

        registerCommitCommand(commandParser);
        registerCheckoutCommand(commandParser);
        registerFeaturesCommand(commandParser);
        registerGetCommand(commandParser);
        registerSetCommand(commandParser);
        registerFetchCommand(commandParser);
        registerPullCommand(commandParser);
        registerPushCommand(commandParser);
        registerForkCommand(commandParser);
        registerRemotesCommand(commandParser);
        registerTracesCommand(commandParser);
        registerDependencyGraphCommand(commandParser);
        registerSuggestConstraintsCommand(commandParser);
        registerMinimizePreviewCommand(commandParser);
    }

    private static void registerCommitCommand(Subparsers commandParser) {
        Subparser commitCommandParser = commandParser.addParser(CommitCommand.COMMIT);
        commitCommandParser.setDefault(ProgramConstants.COMMAND, CommitCommand.COMMIT);
        commitCommandParser.addArgument(CommitCommand.FLAG_CONFIGURATION).required(true);
        commitCommandParser.addArgument(CommitCommand.FLAG_COMMIT_MESSAGE).setDefault("").required(false);
        commandRegister.register(CommitCommand.COMMIT, new CommitCommand(eccoService));
    }

    private static void registerCheckoutCommand(Subparsers commandParser) {
        Subparser checkoutCommandParser = commandParser.addParser(CheckoutCommand.CHECKOUT);
        checkoutCommandParser.setDefault(ProgramConstants.COMMAND, CheckoutCommand.CHECKOUT);
        checkoutCommandParser.addArgument(CheckoutCommand.FLAG_CONFIGURATION).required(true);
        commandRegister.register(CheckoutCommand.CHECKOUT, new CheckoutCommand(eccoService));
    }

    private static void registerFeaturesCommand(Subparsers commandParser) {
        Subparser featuresCommandParser = commandParser.addParser(ListFeaturesCommand.FEATURES);
        featuresCommandParser.setDefault(ProgramConstants.COMMAND, ListFeaturesCommand.FEATURES);
        featuresCommandParser.addArgument(ListFeaturesCommand.NAME_KEY).nargs("?").help("show a single feature and its revisions");
        commandRegister.register(ListFeaturesCommand.FEATURES, new ListFeaturesCommand(eccoService));
    }

    private static void registerGetCommand(Subparsers commandParser) {
        Subparser getCommandParser = commandParser.addParser(GetCommand.GET);
        getCommandParser.setDefault(ProgramConstants.COMMAND, GetCommand.GET);
        getCommandParser.addArgument(GetCommand.PROPERTY_KEY).required(true);
        commandRegister.register(GetCommand.GET, new GetCommand(eccoService));
    }

    private static void registerSetCommand(Subparsers commandParser) {
        Subparser setCommandParser = commandParser.addParser(SetCommand.SET);
        setCommandParser.setDefault(ProgramConstants.COMMAND, SetCommand.SET);
        setCommandParser.addArgument(SetCommand.PROPERTY_KEY).required(true);
        setCommandParser.addArgument(SetCommand.VALUE_KEY).required(true);
        commandRegister.register(SetCommand.SET, new SetCommand(eccoService));
    }

    private static void registerFetchCommand(Subparsers commandParser) {
        Subparser fetchCommandParser = commandParser.addParser(FetchCommand.FETCH);
        fetchCommandParser.setDefault(ProgramConstants.COMMAND, FetchCommand.FETCH);
        fetchCommandParser.addArgument(FetchCommand.REMOTE_KEY).nargs("?").setDefault(EccoService.ORIGIN_REMOTE_NAME);
        commandRegister.register(FetchCommand.FETCH, new FetchCommand(eccoService));
    }

    private static void registerPullCommand(Subparsers commandParser) {
        Subparser pullCommandParser = commandParser.addParser(PullCommand.PULL);
        pullCommandParser.setDefault(ProgramConstants.COMMAND, PullCommand.PULL);
        pullCommandParser.addArgument(PullCommand.REMOTE_KEY).nargs("?").setDefault(EccoService.ORIGIN_REMOTE_NAME);
        pullCommandParser.addArgument(PullCommand.FLAG_EXCLUDE).setDefault("").required(false);
        commandRegister.register(PullCommand.PULL, new PullCommand(eccoService));
    }

    private static void registerPushCommand(Subparsers commandParser) {
        Subparser pushCommandParser = commandParser.addParser(PushCommand.PUSH);
        pushCommandParser.setDefault(ProgramConstants.COMMAND, PushCommand.PUSH);
        pushCommandParser.addArgument(PushCommand.REMOTE_KEY).nargs("?").setDefault(EccoService.ORIGIN_REMOTE_NAME);
        pushCommandParser.addArgument(PushCommand.FLAG_EXCLUDE).setDefault("").required(false);
        commandRegister.register(PushCommand.PUSH, new PushCommand(eccoService));
    }

    private static void registerForkCommand(Subparsers commandParser) {
        Subparser forkCommandParser = commandParser.addParser(ForkCommand.FORK);
        forkCommandParser.setDefault(ProgramConstants.COMMAND, ForkCommand.FORK);
        forkCommandParser.addArgument(ForkCommand.REMOTE_KEY).required(true).help("host:port or path of the repository to fork from");
        forkCommandParser.addArgument(ForkCommand.FLAG_EXCLUDE).setDefault("").required(false);
        commandRegister.register(ForkCommand.FORK, new ForkCommand(eccoService));
    }

    private static void registerRemotesCommand(Subparsers commandParser) {
        Subparser remotesCommandParser = commandParser.addParser(RemotesCommand.REMOTES);
        remotesCommandParser.setDefault(ProgramConstants.COMMAND, RemotesCommand.REMOTES);
        remotesCommandParser.addArgument(RemotesCommand.NAME_KEY).nargs("?").help("show a single remote");
        remotesCommandParser.addArgument(RemotesCommand.FLAG_ADD).nargs(2).metavar("NAME", "ADDRESS").help("add a remote");
        remotesCommandParser.addArgument(RemotesCommand.FLAG_REMOVE).metavar("NAME").help("remove a remote");
        remotesCommandParser.addArgument(RemotesCommand.FLAG_TYPE).choices("LOCAL", "REMOTE").setDefault("REMOTE").help("type of the remote added with " + RemotesCommand.FLAG_ADD);
        commandRegister.register(RemotesCommand.REMOTES, new RemotesCommand(eccoService));
    }

    private static void registerTracesCommand(Subparsers commandParser) {
        Subparser tracesCommandParser = commandParser.addParser(TracesCommand.TRACES);
        tracesCommandParser.setDefault(ProgramConstants.COMMAND, TracesCommand.TRACES);
        tracesCommandParser.addArgument(TracesCommand.ID_KEY).nargs("?").help("show a single trace and its tree");
        commandRegister.register(TracesCommand.TRACES, new TracesCommand(eccoService));
    }

    private static void registerDependencyGraphCommand(Subparsers commandParser) {
        Subparser dependencyGraphCommandParser = commandParser.addParser(DependencyGraphCommand.DG).aliases(DependencyGraphCommand.DEPENDENCY_GRAPH_ALIAS);
        dependencyGraphCommandParser.setDefault(ProgramConstants.COMMAND, DependencyGraphCommand.DG);
        commandRegister.register(DependencyGraphCommand.DG, new DependencyGraphCommand(eccoService));
    }

    private static void registerSuggestConstraintsCommand(Subparsers commandParser) {
        Subparser suggestConstraintsCommandParser = commandParser.addParser(SuggestConstraintsCommand.SUGGEST_CONSTRAINTS);
        suggestConstraintsCommandParser.setDefault(ProgramConstants.COMMAND, SuggestConstraintsCommand.SUGGEST_CONSTRAINTS);
        suggestConstraintsCommandParser.addArgument(SuggestConstraintsCommand.FLAG_MIN_WITNESS)
                .type(Integer.class).setDefault(4).help("minimum number of witnesses before a rule is proposed");
        suggestConstraintsCommandParser.addArgument(SuggestConstraintsCommand.FLAG_CONFIDENCE)
                .type(Double.class).setDefault(0.9).help("confidence threshold in [0,1]; 1.0 disables near-misses");
        commandRegister.register(SuggestConstraintsCommand.SUGGEST_CONSTRAINTS, new SuggestConstraintsCommand(eccoService));
    }

    private static void registerMinimizePreviewCommand(Subparsers commandParser) {
        Subparser minimizePreviewCommandParser = commandParser.addParser(MinimizePreviewCommand.MINIMIZE_PREVIEW);
        minimizePreviewCommandParser.setDefault(ProgramConstants.COMMAND, MinimizePreviewCommand.MINIMIZE_PREVIEW);
        minimizePreviewCommandParser.addArgument(MinimizePreviewCommand.ID_KEY).nargs("?").help("show only a single association's condition");
        minimizePreviewCommandParser.addArgument(MinimizePreviewCommand.FLAG_MIN_WITNESS)
                .type(Integer.class).setDefault(4).help("minimum number of witnesses for a constraint to be trusted");
        minimizePreviewCommandParser.addArgument(MinimizePreviewCommand.FLAG_CONFIDENCE)
                .type(Double.class).setDefault(0.9).help("confidence threshold in [0,1] (only hard, i.e. confidence-1.0, accepted constraints are ever actually applied)");
        commandRegister.register(MinimizePreviewCommand.MINIMIZE_PREVIEW, new MinimizePreviewCommand(eccoService));
    }

    private static void registerSimpleCommand(Subparsers commandParser, String commandString, Command command) {
        commandParser.addParser(commandString).setDefault(ProgramConstants.COMMAND, commandString);
        commandRegister.register(commandString, command);
    }

}
