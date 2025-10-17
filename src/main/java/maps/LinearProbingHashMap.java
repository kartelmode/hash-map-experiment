package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataWrapper;
import internal.FixedSizeQueue;
import internal.ObjectPool;

public class LinearProbingHashMap {
    public static final int MIN_CAPACITY = 16;
    public static final int DEFAULT_LOAD_FACTOR = 50;
    protected final int loadFactor;
    protected static final int NULL = Integer.MIN_VALUE;
    protected final HashCodeComputer hashCodeComputer;

    protected int count = 0;
    protected long collisions = 0;

    protected DataWrapper[] entries;
    protected int lengthMask;

    protected final FixedSizeQueue<DataWrapper> inactiveDataQueue; // contains most recent inactive orders
    protected final ObjectPool<DataWrapper> objectPool;

    LinearProbingHashMap(int activeDataCount, int maxInactiveDataCount, ObjectPool<DataWrapper> objectPool, HashCodeComputer hashCodeComputer) {
        this(activeDataCount, maxInactiveDataCount, objectPool, hashCodeComputer, DEFAULT_LOAD_FACTOR);
    }

    LinearProbingHashMap(int activeDataCount, int maxInactiveDataCount, ObjectPool<DataWrapper> objectPool, HashCodeComputer hashCodeComputer, int loadFactor) {
        if (activeDataCount < MIN_CAPACITY)
            activeDataCount = MIN_CAPACITY;

        if (Integer.bitCount(activeDataCount) != 1) {
            throw new IllegalArgumentException("activeDataCount must be a power of 2");
        }

        if (loadFactor <= 0 || loadFactor > 100) {
            throw new IllegalArgumentException("loadFactor must be between 0 and 100");
        }

        this.loadFactor = loadFactor;

        int totalCacheSize;
        if (maxInactiveDataCount < activeDataCount) {
            totalCacheSize = activeDataCount * 2;
        } else {
            totalCacheSize = maxInactiveDataCount * 2;
        }

        allocTable(totalCacheSize);
        inactiveDataQueue = new FixedSizeQueue<>(maxInactiveDataCount);
        this.objectPool = objectPool;
        this.hashCodeComputer = hashCodeComputer;
    }

    private void onEntryDeleted(DataWrapper data) {
        objectPool.release(data);
    }

    protected void allocTable(int cap) {
        entries = new DataWrapper[cap];
        lengthMask = cap - 1;
    }

    protected void resizeTable(int newSize) {
        final int curLength = entries.length;
        final DataWrapper[] saveOrders = entries;

        allocTable(newSize);
        count = 0;

        for (int i = 0; i < curLength; i++) {
            if (saveOrders[i] != null) {
                putNewNoSpaceCheck(saveOrders[i]);
            }
        }
    }

    protected void putNewNoSpaceCheck(DataWrapper entry) {
        int hidx = hashIndex(entry.getAsciiString());

        putEntry(entry, hidx);
    }

    protected final boolean isFilled(int idx) {
        return (entries[idx] != null);
    }

    protected final boolean isEmpty(int idx) {
        return (entries[idx] == null);
    }

    protected void free(int idx) {
        count--;
        entries[idx].setInCachePosition(-1);
        entries[idx] = null;

        for (int hidx = (idx + 1) & lengthMask; isFilled(hidx); hidx = (hidx + 1) & lengthMask) {
            count--;
            DataWrapper entry = entries[hidx];
            entries[hidx] = null;
            entry.setInCachePosition(-1);
            putNewNoSpaceCheck(entry);
        }
    }

    protected boolean keyEquals(AsciiString a, AsciiString b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    protected int find(AsciiString key) {
        return (find(hashIndex(key), key));
    }

    protected int find(int hidx, AsciiString key) {
        int attempts = 0;
        for (; attempts < entries.length && isFilled(hidx); hidx = (hidx + 1) & lengthMask, attempts++) {
            if (keyEquals(entries[hidx].getAsciiString(), key)) {
                return hidx;
            }
        }
        return NULL;
    }

    protected int hashIndex(AsciiString key) {
        return (hashCodeComputer.modPowerOfTwoHashCode(key, entries.length));
    }

    protected boolean putIfEmpty(DataWrapper entry) {
        int hidx = hashIndex(entry.getAsciiString());
        int idx = find(hidx, entry.getAsciiString());

        if (idx != NULL) {
            return false;
        }

        if (count * 100 >= entries.length * loadFactor) {
            resizeTable(entries.length * 2);
            hidx = hashIndex(entry.getAsciiString());
        }

        putEntry(entry, hidx);
        return true;
    }

    protected DataWrapper getEntry(AsciiString key) {
        int pos = find(key);

        return (pos == NULL) ? null : entries[pos];
    }

    boolean put(DataWrapper entry) {
        final AsciiString orderId = entry.getAsciiString();
        if (!entry.isActive()) {
            int hidx = hashIndex(entry.getAsciiString());
            int idx = find(hidx, entry.getAsciiString());

            if (idx != NULL) {
                return false;
            } else {
                displaceOldestInactiveOrderIfQueueFull();
                if (count * 100 >= entries.length * loadFactor) {
                    resizeTable(entries.length * 2);
                    hidx = hashIndex(entry.getAsciiString());
                }
                putEntry(entry, hidx);

                inactiveDataQueue.put(entry);
                return true;
            }
        } else {
            return putIfEmpty(entry);
        }
    }

    DataWrapper get(AsciiString orderId) {
        DataWrapper entry = getEntry(orderId);
        return entry;
    }

    protected void putEntry(DataWrapper entry, int hidx) {
        int attempts = 0;
        while (attempts < entries.length && isFilled(hidx)) {
            hidx = (hidx + 1) & lengthMask;
            attempts++;
        }
        collisions += (attempts > 0 ? 1 : 0);
        count++;
        entries[hidx] = entry;
        entry.setInCachePosition(hidx);
    }

    protected void displaceOldestInactiveOrderIfQueueFull() {
        if (inactiveDataQueue.isFull()) {
            DataWrapper oldestEntry = inactiveDataQueue.take();
            free(oldestEntry.getInCachePosition());
            onEntryDeleted(oldestEntry);
        }
    }

    void deactivate(DataWrapper entry) {
        assert find(entry.getAsciiString()) != NULL;

        displaceOldestInactiveOrderIfQueueFull();
        inactiveDataQueue.put(entry);
    }

    public long getCollisions() {
        return collisions;
    }
}
