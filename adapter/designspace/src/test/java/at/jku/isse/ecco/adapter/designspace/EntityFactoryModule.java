package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import com.google.inject.AbstractModule;

/**
 * Helper class that configures a binder for the EntityFactory class.
 * Used in the DesignSpaceModule tests.
 * @see EntityFactory
 * @see at.jku.isse.ecco.adapter.designspace.DesignSpaceModuleTest
 */
public class EntityFactoryModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        bind(EntityFactory.class).to(MemEntityFactory.class);
    }
}
