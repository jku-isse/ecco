package at.jku.isse.ecco.web.provider;

import at.jku.isse.ecco.web.domain.repository.AbstractRepository;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Context;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

@Provider
public class RepositoryProvider implements ContextResolver<AbstractRepository> {

    @Context
    private EccoApplication application;

    private ArrayList<AbstractRepository> abstractRepositories = new ArrayList<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryProvider.class);

    @Override
    public AbstractRepository getContext(Class<?> type) {
        Object object = null;
        try {
            Constructor constructor = type.getConstructor(EccoApplication.class);
            constructor.setAccessible(true);
            object = constructor.newInstance(this.application);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        if (!abstractRepositories.contains(object)) {
            abstractRepositories.add((AbstractRepository) object);
            return (AbstractRepository) object;
        } else {
            return abstractRepositories.get(abstractRepositories.indexOf(object));
        }
    }
}

