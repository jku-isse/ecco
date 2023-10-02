package at.jku.isse.ecco.web.provider;

import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.rest.EccoApplication;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@Provider
public class RepositoryProvider implements ContextResolver<AbstractRepository> {
    @Context
    private EccoApplication application;
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryProvider.class);
    @Override
    public AbstractRepository getContext(Class<?> type) {
        Object object = null;
        try {
            Constructor<?> constructor = type.getConstructor(EccoApplication.class);
            constructor.setAccessible(true);
            object = constructor.newInstance(this.application);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return (AbstractRepository) object;
    }
}

