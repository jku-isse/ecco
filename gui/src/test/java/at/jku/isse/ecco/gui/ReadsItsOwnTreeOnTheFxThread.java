package at.jku.isse.ecco.gui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A test that gives a view a tree it made itself and has it read the tree on the FX thread - not the
 * repository's, which no commit changes: {@link RepositoryReadsOffTheFxThread} does not count it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ReadsItsOwnTreeOnTheFxThread {

	/** whose tree, and where the view gets it in the GUI */
	String value();
}
