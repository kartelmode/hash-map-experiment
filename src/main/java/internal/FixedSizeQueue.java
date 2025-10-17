package internal;

public class FixedSizeQueue<E> {
    private final Object[] queue;
    private final int capacity;
    private final int mask;
    private int count;
    private int first; // position of the oldest item
    private int last;  // position where next new item will be inserted

    /**
     * @param capacity must be the power of two
     */
    public FixedSizeQueue(int capacity) {
        if (Integer.bitCount(capacity) != 1)
            throw new IllegalArgumentException("Fixed size queue capacity must be a power of two");
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.first = 0;
        this.last = 0;
        this.queue = new Object[capacity];
        this.count = 0;
    }

    /**
     * @return true if the key was added
     */
    public boolean put(E item) {
        if (count == capacity) {
            return false;
        } else {
            queue[last] = item;
            last = (last + 1) & mask;
            count++;
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    public E take() {
        if (count == 0) {
            return null;
        } else {
            count--;
            E item = (E) queue[first];
            first = (first + 1) & mask;
            return item;
        }
    }

    // Slow
    public boolean remove(E item) {
        for (int i = 0; i < count; i++) {
            if (queue[(i + first) & mask] == item) {
                for (int j = i; j >= 1; j--) {
                    queue[(j + first) & mask] = queue[(j + first - 1) & mask];
                }
                queue[first] = null;
                first = (first + 1) & mask;
                count--;
                return true;
            }
        }
        return false;
    }

    public boolean isFull() {
        return count == capacity;
    }
}
