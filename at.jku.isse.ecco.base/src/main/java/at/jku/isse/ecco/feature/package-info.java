/**
 * <b>ECCO DAL</b>
 * <p>
 * The package is part of data access layer and provides interfaces for database entities that provide a specific
 * technological solution.
 * <p>
 * The solution must provide implementation for certain entity interfaces located in this package but
 * also implementation for interfaces located in {@link at.jku.isse.ecco.dao}, {@link at.jku.isse.ecco.artifact}
 * and {@link at.jku.isse.ecco.tree} packages.
 * <p>
 * The feature package provides interfaces to entities that store which feature the artifacts denote and in which
 * combination they are present.
 * It also provides basic implementations of these interfaces without a data backend for pure in memory
 * implementations.
 * <p>
 * The classes contained in this package usually won't require a specific backend implementation.
 */
package at.jku.isse.ecco.feature;