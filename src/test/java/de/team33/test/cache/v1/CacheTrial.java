package de.team33.test.cache.v1;

import de.team33.libs.testing.v1.Attempts;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class CacheTrial {

    private static final int KEY_COUNT = 100;

    private final Random random = new Random();

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static <K, V> BiFunction<K, Function<K, V>, V> computeIfAbsent(final Map<K, V> map) {
        return map::computeIfAbsent;
    }

    private static <K, V> BiFunction<K, Function<K, V>, V> getOptionalOrElseGet(final Map<K, V> map) {
        return (key, newValue) -> Optional.ofNullable(map.get(key)).orElseGet(() -> {
            final V result = newValue.apply(key);
            map.put(key, result);
            return result;
        });
    }

    private static <K, V> BiFunction<K, Function<K, V>, V> getOptionalOrElseGetAtomic(final Map<K, V> map) {
        final Map<K, AtomicInteger> counters = new ConcurrentHashMap<>();
        return new BiFunction<K, Function<K, V>, V>() {
            @Override
            public V apply(final K key, final Function<K, V> newValue) {
                return Optional.ofNullable(map.get(key)).orElseGet(() -> {
                    final AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
                    final int cv = counter.incrementAndGet();
                    if (1 == cv) {
                        final V result = newValue.apply(key);
                        map.put(key, result);
                        return result;
                    } else {
                        counter.decrementAndGet();
                    }
                    Thread.yield();
                    return this.apply(key, newValue);
                });
            }
        };
    }

    private static <K, V> BiFunction<K, Function<K, V>, V> getOptionalOrElseGetSync(final Map<K, V> map) {
        return (key, newValue) -> Optional.ofNullable(map.get(key)).orElseGet(() -> {
            synchronized (map) {
                return Optional.ofNullable(map.get(key)).orElseGet(() -> {
                    final V result = newValue.apply(key);
                    map.put(key, result);
                    return result;
                });
            }
        });
    }

    @Test//(expected = AssertionError.class)
    public final void get_ComputeIfAbsent_SimpleHashMap() {
        // This should fail, because the cache is not atomic enough ...
        assertAllSingle(get(new HashMap<>(), CacheTrial::computeIfAbsent));
    }

    @Test
    public final void get_ComputeIfAbsent_SynchrinizedMap() {
        // This should work ...
        assertAllSingle(get(Collections.synchronizedMap(new HashMap<>()), CacheTrial::computeIfAbsent));
    }

    @Test
    public final void get_ComputeIfAbsent_ConcurrentHashMap() {
        // This should work ...
        assertAllSingle(get(new ConcurrentHashMap<>(), CacheTrial::computeIfAbsent));
    }

    @Test//(expected = AssertionError.class)
    public final void get_OptionalOrElseGet_SimpleHashMap() {
        // This should fail, because the cache is not atomic enough ...
        assertAllSingle(get(new HashMap<>(), CacheTrial::getOptionalOrElseGet));
    }

    @Test//(expected = AssertionError.class)
    public final void get_OptionalOrElseGet_SynchrinizedMap() {
        // This should fail, because the cache is not atomic enough ...
        assertAllSingle(get(Collections.synchronizedMap(new HashMap<>()), CacheTrial::getOptionalOrElseGet));
    }

    @Test//(expected = AssertionError.class)
    public final void get_OptionalOrElseGet_ConcurrentHashMap() {
        // This should fail, because the cache is not atomic enough ...
        assertAllSingle(get(new ConcurrentHashMap<>(), CacheTrial::getOptionalOrElseGet));
    }

    @Test
    public final void get_OptionalOrElseGetSync_SimpleHashMap() {
        // This should work ...
        assertAllSingle(get(new HashMap<>(), CacheTrial::getOptionalOrElseGetSync));
    }

    @Test
    public final void get_OptionalOrElseGetSync_SynchrinizedMap() {
        // This should work ...
        assertAllSingle(get(Collections.synchronizedMap(new HashMap<>()), CacheTrial::getOptionalOrElseGetSync));
    }

    @Test
    public final void get_OptionalOrElseGetSync_ConcurrentHashMap() {
        // This should work ...
        assertAllSingle(get(new ConcurrentHashMap<>(), CacheTrial::getOptionalOrElseGetSync));
    }

    @Test(timeout = 10000)
    public final void get_OptionalOrElseGetAtomic_SimpleHashMap() {
        // This should work ...
        assertAllSingle(get(new HashMap<>(), CacheTrial::getOptionalOrElseGetAtomic));
    }

    @Test
    public final void get_OptionalOrElseGetAtomic_SynchrinizedMap() {
        // This should work ...
        assertAllSingle(get(Collections.synchronizedMap(new HashMap<>()), CacheTrial::getOptionalOrElseGetAtomic));
    }

    @Test
    public final void get_OptionalOrElseGetAtomic_ConcurrentHashMap() {
        // This should work ...
        assertAllSingle(get(new ConcurrentHashMap<>(), CacheTrial::getOptionalOrElseGetAtomic));
    }

    private Map<Integer, AtomicInteger> get(final Map<Integer, Value> cache,
                                            final Function<
                                                    Map<Integer, Value>,
                                                    BiFunction<Integer, Function<Integer, Value>, Value>> method) {
        final Map<Integer, AtomicInteger> counters = new ConcurrentHashMap<>(0);
        final Function<Integer, Value> newValue = value -> {
            counters.computeIfAbsent(value, ignored -> new AtomicInteger(0))
                    .incrementAndGet();
            //sleep(1);
            Thread.yield();
            return new Value(value);
        };
        final BiFunction<Integer, Function<Integer, Value>, Value> getter = method.apply(cache);

        Attempts.tryParallel(5000, () -> {
            Attempts.trySerial(5000, () -> {
                final int key = nextInt(KEY_COUNT);
                final Value result = getter.apply(key, newValue);
                final Value expected = new Value(key);
                assertEquals(expected, result);
            });
        });

        return counters;
    }

    private void assertAllSingle(final Map<Integer, AtomicInteger> counters) {
        // Every possible <key> from 0 to KEY_COUNT - 1 must have occurred at least once.
        // If not, increase the number of attempts!
        assertEquals(KEY_COUNT, counters.size());

        // Each counter must have been counted up exactly once.
        // If not, access to the cache is not atomic enough.
        counters.values()
                .forEach(counter -> assertEquals(1, counter.get()));
    }

    private int nextInt(final int bound) {
        synchronized (random) {
            return random.nextInt(bound);
        }
    }
}