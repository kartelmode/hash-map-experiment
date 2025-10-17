package internal;

import java.util.Arrays;

public class ObjectPool<T> {
    public interface Factory<T> {
        T create();
    }

    private final Factory<T> factory;

    private Object[] array;
    private int size;

    public ObjectPool(final int initialSize, final Factory<T> factory) {
        final Object[] array = new Object[(initialSize == 0) ? 1 : initialSize];

        for (int i = 0; i < initialSize; i++) {
            final T item = factory.create();
            assert item != null;
            array[i] = item;
        }

        this.size = initialSize;
        this.factory = factory;
        this.array = array;
    }

    @SuppressWarnings("unchecked")
    public T borrow() {
        if (size > 0) {
            final int last = --size;
            final Object item = array[last];
            array[last] = null; // clear reference to borrowed item
            assert item != null;
            return (T) item;
        } else {
            return factory.create();
        }
    }

    public void release(final T item) {
        if (item != null) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }

            array[size++] = item;
        }
    }
}
