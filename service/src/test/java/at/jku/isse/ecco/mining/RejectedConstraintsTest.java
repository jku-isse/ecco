package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rejected suggestions used to be kept in this machine's preferences, so a collaborator who forked or
 * pulled the repository was offered every suggestion someone else had already rejected. They are
 * stored in the repository now, next to the accepted ones.
 */
public class RejectedConstraintsTest {

    private static final ConstraintMiner.Suggestion MANDATORY_A = suggestion(ConstraintMiner.Kind.MANDATORY, "A", null);
    private static final ConstraintMiner.Suggestion B_REQUIRES_A = suggestion(ConstraintMiner.Kind.REQUIRES, "B", "A");
    private static final ConstraintMiner.Suggestion A_EXCLUDES_C = suggestion(ConstraintMiner.Kind.EXCLUDES, "A", "C");

    @Test
    @Timeout(30)
    public void rejectionsAreStoredInTheRepositoryAndSurviveReopening(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        try (EccoService service = repository(repo)) {
            service.rejectConstraints(List.of(MANDATORY_A, B_REQUIRES_A));
            service.unrejectConstraints(List.of(ConstraintSuggestionPreferences.parseSignature(signature(B_REQUIRES_A))));
        }
        try (EccoService service = reopen(repo)) {
            assertEquals(Set.of(signature(MANDATORY_A)), signatures(service.getRepository().getRejectedConstraints()));
        }
        assertTrue(ConstraintSuggestionPreferences.getRejected(repo.resolve(".ecco")).isEmpty(), "nothing is kept per machine");
    }

    @Test
    @Timeout(30)
    public void acceptingAndRejectingWithdrawEachOther(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.acceptConstraints(List.of(MANDATORY_A));
            service.rejectConstraints(List.of(MANDATORY_A));
            assertEquals(Set.of(), signatures(service.getRepository().getConstraints()));
            assertEquals(Set.of(signature(MANDATORY_A)), signatures(service.getRepository().getRejectedConstraints()));

            service.acceptConstraints(List.of(MANDATORY_A));
            assertEquals(Set.of(signature(MANDATORY_A)), signatures(service.getRepository().getConstraints()));
            assertEquals(Set.of(), signatures(service.getRepository().getRejectedConstraints()));
        }
    }

    @Test
    @Timeout(60)
    public void rejectionsTravelWithForkAndTheReceiverKeepsItsOwnDecisions(@TempDir Path tmp) throws Exception {
        Path origin = tmp.resolve("origin");
        try (EccoService service = repository(origin)) {
            service.rejectConstraints(List.of(MANDATORY_A, B_REQUIRES_A));
            service.acceptConstraints(List.of(A_EXCLUDES_C));
        }

        Path fork = Files.createDirectories(tmp.resolve("fork"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.fork(origin.resolve(".ecco"));
            assertEquals(Set.of(signature(MANDATORY_A), signature(B_REQUIRES_A)), signatures(service.getRepository().getRejectedConstraints()));
            assertEquals(Set.of(signature(A_EXCLUDES_C)), signatures(service.getRepository().getConstraints()));

            // the fork changes its mind on two of them
            service.acceptConstraints(List.of(MANDATORY_A));
            service.rejectConstraints(List.of(A_EXCLUDES_C));
        }

        // pulled back, the origin keeps its own decisions on all three
        try (EccoService service = reopen(origin)) {
            service.addRemote("fork", fork.toString(), at.jku.isse.ecco.core.Remote.Type.LOCAL);
            service.pull("fork");
            assertEquals(Set.of(signature(MANDATORY_A), signature(B_REQUIRES_A)), signatures(service.getRepository().getRejectedConstraints()));
            assertEquals(Set.of(signature(A_EXCLUDES_C)), signatures(service.getRepository().getConstraints()));
        }

        // a repository that has decided nothing takes the other side's decisions
        Path fresh = tmp.resolve("fresh");
        try (EccoService service = repository(fresh)) {
            service.addRemote("fork", fork.toString(), at.jku.isse.ecco.core.Remote.Type.LOCAL);
            service.pull("fork");
            assertEquals(Set.of(signature(B_REQUIRES_A), signature(A_EXCLUDES_C)), signatures(service.getRepository().getRejectedConstraints()));
            assertEquals(Set.of(signature(MANDATORY_A)), signatures(service.getRepository().getConstraints()));
        }
    }

    @Test
    @Timeout(30)
    public void rejectionsKeptPerMachineMoveIntoTheRepository(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Path repositoryDir = repo.resolve(".ecco");
        try (EccoService service = repository(repo)) {
            service.acceptConstraints(List.of(A_EXCLUDES_C));
            // what earlier versions wrote when a suggestion was rejected
            ConstraintSuggestionPreferences.reject(repositoryDir, signature(MANDATORY_A));
            ConstraintSuggestionPreferences.reject(repositoryDir, signature(A_EXCLUDES_C));

            assertEquals(2, service.moveLocalRejectionsIntoRepository());
            assertEquals(0, service.moveLocalRejectionsIntoRepository(), "moved only once");

            assertEquals(Set.of(signature(MANDATORY_A)), signatures(service.getRepository().getRejectedConstraints()));
            assertEquals(Set.of(signature(A_EXCLUDES_C)), signatures(service.getRepository().getConstraints()),
                    "an acceptance in the repository wins over an older local rejection");
        } finally {
            ConstraintSuggestionPreferences.forget(repositoryDir);
        }
    }

    @Test
    @Timeout(30)
    public void aRepositoryWrittenBeforeRejectionsWereStoredGainsThem(@TempDir Path tmp) throws Exception {
        // an empty repository an earlier build wrote (2026-08-05): its core file has no rejected-constraints field
        Path old = Path.of("src/test/resources/repositories/before-rejected-constraints/.ecco");
        Path repo = tmp.resolve("old");
        try (var paths = Files.walk(old)) {
            for (Path p : paths.toList()) {
                Path target = repo.resolve(".ecco").resolve(old.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        try (EccoService service = reopen(repo)) {
            assertEquals(Set.of(), signatures(service.getRepository().getRejectedConstraints()));
            service.rejectConstraints(List.of(MANDATORY_A));
        }
        try (EccoService service = reopen(repo)) {
            assertEquals(Set.of(signature(MANDATORY_A)), signatures(service.getRepository().getRejectedConstraints()));
        }
    }

    @Test
    @Timeout(30)
    public void mergeCopiesRejectionsLikeAcceptedConstraints() {
        Repository.Op source = new at.jku.isse.ecco.storage.ser.repository.SerRepository();
        Repository.Op target = new at.jku.isse.ecco.storage.ser.repository.SerRepository();
        source.addRejectedConstraint(Constraint.Kind.MANDATORY, "A", null);
        source.addConstraint(Constraint.Kind.EXCLUDES, "A", "C");
        target.addConstraint(Constraint.Kind.MANDATORY, "A", null);

        target.merge(source);

        assertEquals(Set.of(signature(MANDATORY_A), signature(A_EXCLUDES_C)), signatures(target.getConstraints()));
        assertEquals(Set.of(), signatures(target.getRejectedConstraints()));
    }

    private static EccoService repository(Path dir) throws Exception {
        Files.createDirectories(dir);
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        return service;
    }

    private static EccoService reopen(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.setBaseDir(dir);
        service.open();
        return service;
    }

    private static ConstraintMiner.Suggestion suggestion(ConstraintMiner.Kind kind, String a, String b) {
        return new ConstraintMiner.Suggestion(kind, a, b, 1.0, 1.0, 5, List.of());
    }

    private static String signature(ConstraintMiner.Suggestion suggestion) {
        return ConstraintSuggestionPreferences.signatureOf(suggestion);
    }

    private static Set<String> signatures(Collection<? extends Constraint> constraints) {
        return constraints.stream().map(Constraint::getId).collect(Collectors.toSet());
    }
}
