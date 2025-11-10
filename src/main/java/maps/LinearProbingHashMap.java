package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;
import internal.FixedSizeQueue;

public class LinearProbingHashMap implements Cache {
    public static final int MIN_CAPACITY = 16;
    public static final int DEFAULT_LOAD_FACTOR = 50;
    protected final int loadFactor;
    protected static final int NULL = Integer.MIN_VALUE;
    protected final HashCodeComputer hashCodeComputer;

    protected int count = 0;
    protected long collisions = 0;

    protected DataPayload[] entries;
    protected int lengthMask;

    protected final FixedSizeQueue<DataPayload> inactiveDataQueue; // contains most recent inactive orders

    LinearProbingHashMap(int activeDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer) {
        this(activeDataCount, maxInactiveDataCount, hashCodeComputer, DEFAULT_LOAD_FACTOR);
    }

    LinearProbingHashMap(int activeDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer, int loadFactor) {
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
        this.hashCodeComputer = hashCodeComputer;
    }

    protected void allocTable(int cap) {
        entries = new DataPayload[cap];
        lengthMask = cap - 1;
    }

    protected void resizeTable(int newSize) {
        final int curLength = entries.length;
        final DataPayload[] saveOrders = entries;

        allocTable(newSize);
        count = 0;

        for (int i = 0; i < curLength; i++) {
            if (saveOrders[i] != null) {
                putNewNoSpaceCheck(saveOrders[i]);
            }
        }
    }

    protected void putNewNoSpaceCheck(DataPayload entry) {
        int hidx = hashIndex(entry.getKey());

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

        compactChain(idx);
    }

    private void compactChain(int deletedIdx) {
        int curIdx = deletedIdx;

        while (true) {
            curIdx = (curIdx + 1) & lengthMask;
            if (isEmpty(curIdx)) {
                break;
            }

            DataPayload entry = entries[curIdx];
            int hidx = hashIndex(entries[curIdx].getKey());
            if ((curIdx < hidx && (hidx <= deletedIdx || deletedIdx <= curIdx)) ||
                    (hidx <= deletedIdx && deletedIdx <= curIdx)) {
                entries[deletedIdx] = entry;
                entries[curIdx] = null;
                entry.setInCachePosition(deletedIdx);
                deletedIdx = curIdx;
            }
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
            if (keyEquals(entries[hidx].getKey(), key)) {
                return hidx;
            }
        }
        return NULL;
    }

    protected int hashIndex(AsciiString key) {
        return (hashCodeComputer.modPowerOfTwoHashCode(key, entries.length));
    }

    @Override
    public boolean putIfEmpty(DataPayload entry) {
        int hidx = hashIndex(entry.getKey());
        int idx = find(hidx, entry.getKey());

        if (idx != NULL) {
            return false;
        }

        if (count * 100 >= entries.length * loadFactor) {
            resizeTable(entries.length * 2);
            hidx = hashIndex(entry.getKey());
        }

        putEntry(entry, hidx);
        return true;
    }

    @Override
    public DataPayload get(AsciiString key) {
        int pos = find(key);

        return (pos == NULL) ? null : entries[pos];
    }

    protected void putEntry(DataPayload entry, int hidx) {
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
            DataPayload oldestEntry = inactiveDataQueue.take();
            free(oldestEntry.getInCachePosition());
        }
    }

    @Override
    public void deactivate(DataPayload entry) {
        assert find(entry.getKey()) != NULL;

        displaceOldestInactiveOrderIfQueueFull();
        inactiveDataQueue.put(entry);
    }

    @Override
    public long collisionCount() {
        return collisions;
    }

    @Override
    public int capacity() {
        return entries.length;
    }

    @Override
    public int size () {
        return count;
    }

}
