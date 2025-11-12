package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;
import internal.FixedSizeQueue;

import java.util.HashMap;

public class JavaHashMap implements Cache {
    public static final int MIN_CAPACITY = 16;

    private final HashMap<AsciiString, DataPayload> map;

    private final FixedSizeQueue<DataPayload> inactiveDataQueue;

    public JavaHashMap(int initialActiveDataCount, int maxInactiveDataCount) {
        if (initialActiveDataCount < MIN_CAPACITY)
            initialActiveDataCount = MIN_CAPACITY;

        if (Integer.bitCount(maxInactiveDataCount) != 1) {
            throw new IllegalArgumentException("maxInactiveDataCount must be a power of 2");
        }

        this.map = new HashMap<>(initialActiveDataCount);
        this.inactiveDataQueue = new FixedSizeQueue<>(maxInactiveDataCount);
    }

    @Override
    public boolean putIfEmpty(DataPayload entry) {
        if (map.containsKey(entry.getKey())) {
            return false;
        }
        return map.put(entry.getKey(), entry) == null;
    }

    @Override
    public DataPayload get(AsciiString key) {
        return map.get(key);
    }

    @Override
    public void deactivate(DataPayload entry) {
        if (inactiveDataQueue.isFull()) {
            map.remove(inactiveDataQueue.take().getKey());
        }
        inactiveDataQueue.put(entry);
    }

    @Override
    public long collisionCount() {
        return 0;
    }

    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public int size() {
        return map.size();
    }
}
