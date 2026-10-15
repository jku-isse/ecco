package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.feature.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Known gap #47: only the commit rolled its transaction back on an {@link Error} (such as
 * OutOfMemoryError). Every other operation caught only Exception, so an Error left its transaction
 * open: transactions nest by a counter, so each later operation became part of the stuck one - its
 * writes were never stored - and close() failed with "Not all transactions have been ended".
 */
public class ErrorRollbackTest {

    /** Stands for an OutOfMemoryError without having to run out of memory. */
    private static class InjectedError extends Error {
        InjectedError() {
            super("injected");
        }
    }

    @Test
    @Timeout(120)
    public void anErrorRollsBackTheTransactionOfEveryOperation() throws Exception {
        // the configuration is parsed before the storage fails, so that the operation itself runs: built
        // inside the call, its parse failed first and addVariant, checkout and checkConstraintViolations
        // were never reached
        Map<String, BiConsumer<EccoService, Configuration>> operations = new LinkedHashMap<>();
        operations.put("getRepository", (s, c) -> s.getRepository());
        operations.put("parseConfigurationString", (s, c) -> s.parseConfigurationString("A"));
        operations.put("addVariant", (s, c) -> s.addVariant(c, "v"));
        operations.put("removeVariant (writeTransaction)", (s, c) -> s.removeVariant("none"));
        operations.put("store", (s, c) -> s.store());
        operations.put("checkout", (s, c) -> s.checkout(c));
        operations.put("validCheckoutConditions", (s, c) -> s.validCheckoutConditions());
        operations.put("minimizeConditionsForCheckout", (s, c) -> s.minimizeConditionsForCheckout());
        operations.put("checkConstraintViolations", (s, c) -> s.checkConstraintViolations(c));
        operations.put("map", (s, c) -> s.map(List.of(Path.of("f.txt"))));
        operations.put("addRemote", (s, c) -> s.addRemote("other", "localhost:1"));
        operations.put("getRemote", (s, c) -> s.getRemote("origin"));
        operations.put("getRemotes", (s, c) -> s.getRemotes());
        operations.put("removeRemote", (s, c) -> s.removeRemote("origin"));

        for (Map.Entry<String, BiConsumer<EccoService, Configuration>> operation : operations.entrySet())
            assertRollsBack(operation.getKey(), operation.getValue());
    }

    @Test
    @Timeout(120)
    public void anErrorReadingTheOriginRollsBackItsTransaction() throws Exception {
        Path origin = newRepository();
        Path target = newRepository();
        try (EccoService originService = open(origin); EccoService targetService = open(target)) {
            RepositoryDao repositoryDao = originService.repositoryDao;
            originService.repositoryDao = failing(RepositoryDao.class, repositoryDao);
            // forkAlreadyOpen reads the origin through getRepository(), which loads it
            assertThrows(InjectedError.class, () -> targetService.forkAlreadyOpen(originService, ""));
            originService.repositoryDao = repositoryDao;
            assertNotNull(originService.getRepository());
        }
    }

    private static Configuration configuration(EccoService service) {
        return service.parseConfigurationString("A");
    }

    private void assertRollsBack(String name, BiConsumer<EccoService, Configuration> operation) throws Exception {
        Path repositoryDir = newRepository();
        EccoService service = open(repositoryDir);
        Configuration configuration = configuration(service);
        service.setBaseDir(Files.createTempDirectory("error-rollback-out"));
        service.addRemote("origin", "localhost:2");

        RepositoryDao repositoryDao = service.repositoryDao;
        RemoteDao remoteDao = service.remoteDao;
        service.repositoryDao = failing(RepositoryDao.class, repositoryDao);
        service.remoteDao = failing(RemoteDao.class, remoteDao);
        Executable call = () -> operation.accept(service, configuration);
        assertThrows(InjectedError.class, call, name + ": the Error itself, not wrapped");
        service.repositoryDao = repositoryDao;
        service.remoteDao = remoteDao;

        // a write after the Error is stored, and the repository closes
        service.addVariant(configuration, "after " + name);
        service.close();

        try (EccoService reopened = open(repositoryDir)) {
            assertEquals(1, reopened.getRepository().getVariants().size(), name + ": the later write was stored");
            assertTrue(reopened.getRemotes().stream().map(Remote::getName).anyMatch("origin"::equals), name);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T failing(Class<T> type, T delegate) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            String m = method.getName();
            if (m.startsWith("load") || m.startsWith("store") || m.startsWith("remove"))
                throw new InjectedError();
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    private static Path newRepository() throws Exception {
        Path workDir = Files.createTempDirectory("error-rollback");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(content.resolve("f.txt"), "a\n");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            service.commit("commit", "A");
        }
        return workDir.resolve(".ecco");
    }

    private static EccoService open(Path repositoryDir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(repositoryDir);
        service.open();
        return service;
    }
}
