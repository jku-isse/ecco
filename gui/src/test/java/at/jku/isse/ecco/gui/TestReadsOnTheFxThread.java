package at.jku.isse.ecco.gui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A test that reads trees on the FX thread itself - handing a view a tree it made, which no commit
 * changes, or reading the repository's for its own assertions, through a view's classes: no view's
 * read, which {@link RepositoryReadsOffTheFxThread} does not count.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestReadsOnTheFxThread {

	/** what the test reads, and why no view does so in the GUI */
	String value();
}
