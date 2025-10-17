package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataWrapper;
import internal.FixedSizeQueue;
import internal.ObjectPool;

import java.util.Arrays;

public class ChainingHashMap {
    public static final int MIN_CAPACITY = 16;

    private static final int  NULL = Integer.MIN_VALUE;
    private final HashCodeComputer hashCodeComputer;

    private int               count = 0;
    private int               freeHead;
    private int []            hashIndex;
    private int []            next;
    private int []            prev;

    private DataWrapper[] entries;

    private long collisions = 0;

    private final FixedSizeQueue<DataWrapper> inactiveDataQueue; // contains most recent inactive orders

    private final ObjectPool<DataWrapper> objectPool;

    ChainingHashMap(int initialActiveDataCount, int maxInactiveDataCount, ObjectPool<DataWrapper> objectPool, HashCodeComputer hashCodeComputer) {
        if (initialActiveDataCount < MIN_CAPACITY)
            initialActiveDataCount = MIN_CAPACITY;

        if (Integer.bitCount(maxInactiveDataCount) != 1) {
            throw new IllegalArgumentException("maxInactiveDataCount must be a power of 2");
        }

        allocTable(initialActiveDataCount);
        this.inactiveDataQueue = new FixedSizeQueue<>(maxInactiveDataCount);
        this.objectPool = objectPool;
        this.hashCodeComputer = hashCodeComputer;
    }

    private void onEntryDeleted(DataWrapper orderEntry) {
        objectPool.release(orderEntry);
    }

    private void allocTable (int cap) {
        entries = new DataWrapper [cap];
        hashIndex = new int [cap];
        next = new int [cap];
        prev = new int [cap];

        format ();
    }

    private void format() {
        count = 0;

        Arrays.fill(hashIndex, NULL);
        Arrays.fill(prev, NULL);

        int cap = prev.length;

        freeHead = cap - 1;

        next[0] = NULL;

        for (int ii = 1; ii < cap; ii++)
            next[ii] = ii - 1;
    }

    private void free(int idx) {
        //
        // Remove [idx] from chain
        //
        int nx = next[idx];
        int pv = prev[idx];

        if (nx != NULL)
            prev[nx] = pv;

        if (pv < 0)
            hashIndex[-pv - 1] = nx;
        else
            next[pv] = nx;
        //
        // Link [idx] to free list
        //
        next[idx] = freeHead;
        prev[idx] = NULL;  // prev must be NULL in free list
        freeHead = idx;
        count--;

        entries[idx].setInCachePosition(-1);
        entries[idx] = null;
    }

    protected int allocEntry(int hidx) {
        assert freeHead != NULL : "Free list is empty. Cannot expand here.";
        assert isEmpty(freeHead) : "Element [freeHead=" + freeHead + "] is not free";

        int newChainHeadIdx = freeHead;

        freeHead = next[newChainHeadIdx];

        int oldChainHeadIdx = hashIndex[hidx];

        next[newChainHeadIdx] = oldChainHeadIdx;

        if (oldChainHeadIdx != NULL) {
            prev[oldChainHeadIdx] = newChainHeadIdx;
            collisions++;
        }

        prev[newChainHeadIdx] = -hidx - 1;
        hashIndex[hidx] = newChainHeadIdx;

        count++;
        return (newChainHeadIdx);
    }

    protected final boolean isEmpty(int idx) {
        return (prev[idx] == NULL);
    }

    private boolean putIfEmpty(DataWrapper entry) {
        AsciiString key = entry.getAsciiString();
        int hidx = hashIndex(key);
        int idx = find(hidx, key);

        if (idx != NULL) {
            return false;
        }

        if (freeHead == NULL) {
            resizeTable(entries.length * 2);
            hidx = hashIndex(key); // recompute!
        }

        idx = allocEntry(hidx);

        putEntry(idx, entry);

        return true;
    }

    private boolean putInCache(DataWrapper entry) {
        AsciiString key = entry.getAsciiString();
        int         hidx = hashIndex (key);
        int         idx = find (hidx, key);

        if (idx != NULL) {
            entries[idx] = entry;
            entry.setInCachePosition(idx);
            return false;
        }

        if (freeHead == NULL) {
            resizeTable (entries.length * 2);
            hidx = hashIndex (key); // recompute!
        }

        idx = allocEntry(hidx);

        putEntry(idx, entry);

        return true;
    }

    private void putNewNoSpaceCheck(DataWrapper entry) {
        AsciiString key = entry.getAsciiString();
        int hidx = hashIndex(key);
        int idx = find(hidx, key);

        if (idx != NULL)
            throw new IllegalArgumentException(
                    "Value for key " + key + " already exists = " + entry
            );

        idx = allocEntry(hidx);

        putEntry(idx, entry);
    }

    private void resizeTable(int newSize) {
        final int curLength = entries.length;
        final DataWrapper[] saveEntries = entries;
        final int[] savePrev = prev;

        allocTable(newSize);

        for (int ii = 0; ii < curLength; ii++)
            if (savePrev[ii] != NULL)
                putNewNoSpaceCheck(saveEntries[ii]);
    }

    private int find(AsciiString key) {
        return (find(hashIndex(key), key));
    }

    private int find(int hidx, AsciiString key) {
        for (int chain = hashIndex[hidx]; chain != NULL; chain = next[chain]) {
            assert hashIndex(entries[chain].getAsciiString()) == hidx;

            if (keyEquals(key, entries[chain] == null ? null : entries[chain].getAsciiString()))
                return chain;
        }

        return (NULL);
    }

    private boolean keyEquals(AsciiString a, AsciiString b) {
        if (a == null)
            return (b == null);
        return a.equals(b);
    }

    private int hashIndex(AsciiString key) {
        return hashCodeComputer.modPowerOfTwoHashCode(key, hashIndex.length);
    }

    protected void putEntry(int idx, DataWrapper entry) {
        entries[idx] = entry;
        entry.setInCachePosition(idx);
    }

    private void displaceOldestInactiveOrderIfQueueFull() {
        if (inactiveDataQueue.isFull()) {
            DataWrapper oldEntry = inactiveDataQueue.take();
            free(oldEntry.getInCachePosition());
            onEntryDeleted(oldEntry);
        }
    }

    private DataWrapper getEntry(AsciiString key) {
        int pos = find(key);

        return (pos == NULL) ? null : entries[pos];
    }

    /**
     * @return true if inserted.
     */
    boolean put(DataWrapper entry) {
        if (!entry.isActive()) {
            //assert ! activeOrdersCache.containsKey(orderId);
            int hidx = hashIndex(entry.getAsciiString());
            int idx = find(hidx, entry.getAsciiString());

            if (idx != NULL) {
                return false;
            } else {
                displaceOldestInactiveOrderIfQueueFull();
                if (count == entries.length) {
                    resizeTable(entries.length * 2);
                    hidx = hashIndex(entry.getAsciiString());
                }
                idx = allocEntry(hidx);
                putEntry(idx, entry);

                inactiveDataQueue.put(entry);
                return true;
            }
        } else {
            return putIfEmpty(entry);
        }
    }

    DataWrapper get(AsciiString key) {
        DataWrapper entry = getEntry(key);
        assert entry == null || key.equals(entry.getAsciiString());
        return entry;
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
