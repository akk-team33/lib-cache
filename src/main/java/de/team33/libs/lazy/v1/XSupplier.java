package de.team33.libs.lazy.v1;

import java.util.function.Supplier;

/**
 * A kind of supplier that allows to throw a checked exception.
 *
 * @param <T> the type of results supplied by this supplier
 * @param <X> the type of exception that may be thrown by this supplier's method
 *
 * @see Supplier
 */
@FunctionalInterface
public interface XSupplier<T, X extends Exception> {

    /**
     * Gets a result.
     *
     * @see Supplier#get()
     */
    T get() throws X;
}
