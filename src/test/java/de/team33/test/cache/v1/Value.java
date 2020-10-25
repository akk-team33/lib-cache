package de.team33.test.cache.v1;

class Value {

    private final int value;

    Value(final int value) {
        this.value = value;
    }

    @Override
    public final int hashCode() {
        return value;
    }

    @Override
    public final boolean equals(final Object obj) {
        return (this == obj) || ((obj instanceof Value) && (value == ((Value) obj).value));
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
