package de.team33.libs.lazy.v1;

import java.util.function.Supplier;

/**
 * <p>Implements a {@link Supplier} that provides a virtually fixed value.
 * This value is only actually determined when it is accessed for the first time.</p>
 *
 * <p>This implementation ensures that the {@linkplain #Lazy(Supplier) originally defined initialization code}
 * is called at most once, even when there is concurrent access from multiple threads.</p>
 */
public class Lazy<T> implements Supplier<T> {

    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private Supplier<T> backing;

    /**
     * Initializes a new instance giving a supplier that defines the intended initialization of the represented value.
     */
    public Lazy(final Supplier<T> initial) {
        //noinspection AnonymousInnerClass
        this.backing = new Supplier<T>() {
            @Override
            public synchronized T get() {
                if (backing == this) {
                    final T result = initial.get();
                    backing = () -> result;
                }
                return backing.get();
            }
        };
    }

    /**
     * <p>Returns the represented value.</p>
     * <p>That value was determined once with the first access using the
     * {@linkplain #Lazy(Supplier) originally defined initialization code}.</p>
     */
    @Override
    public final T get() {
        return backing.get();
    }
}
