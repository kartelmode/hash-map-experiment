package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;
import internal.FixedSizeQueue;

import java.util.Arrays;

public final class ChainingHashMap implements Cache {
    public static final int MIN_CAPACITY = 16;

    private static final int  NULL = Integer.MIN_VALUE;
    private final HashCodeComputer hashCodeComputer;

    private int               count = 0;
    private int               freeHead;
    private int []            hashIndex;
    private int []            next;
    private int []            prev;

    private DataPayload[]     entries;

    private long collisions = 0;

    private final FixedSizeQueue<DataPayload> inactiveDataQueue;

    public ChainingHashMap(int initialActiveDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer) {
        if (initialActiveDataCount < MIN_CAPACITY)
            initialActiveDataCount = MIN_CAPACITY;

        if (Integer.bitCount(maxInactiveDataCount) != 1) {
            throw new IllegalArgumentException("maxInactiveDataCount must be a power of 2");
        }

        allocTable(initialActiveDataCount);
        this.inactiveDataQueue = new FixedSizeQueue<>(maxInactiveDataCount);
        this.hashCodeComputer = hashCodeComputer;
    }

    private void allocTable (int cap) {
        entries = new DataPayload [cap];
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

    private int allocEntry(int hidx) {
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

    private boolean isEmpty(int idx) {
        return (prev[idx] == NULL);
    }

    @Override
    public boolean putIfEmpty(DataPayload entry) {
        if (freeHead == NULL) {
            resizeTable(entries.length * 2);
        }

        AsciiString key = entry.getKey();
        int hidx = hashIndex(key);
        int idx = find(hidx, key);

        if (idx != NULL) {
            return false;
        }

        idx = allocEntry(hidx);

        putEntry(idx, entry);

        return true;
    }

    private void putNewNoSpaceCheck(DataPayload entry) {
        AsciiString key = entry.getKey();
        int hidx = hashIndex(key);
        int idx = allocEntry(hidx);

        putEntry(idx, entry);
    }

    private void resizeTable(int newSize) {
        final int curLength = entries.length;
        final DataPayload[] saveEntries = entries;
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
            assert hashIndex(entries[chain].getKey()) == hidx;

            if (keyEquals(key, entries[chain] == null ? null : entries[chain].getKey()))
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

    private void putEntry(int idx, DataPayload entry) {
        entries[idx] = entry;
        entry.setInCachePosition(idx);
    }

    private void displaceOldestInactiveOrderIfQueueFull() {
        if (inactiveDataQueue.isFull()) {
            DataPayload oldEntry = inactiveDataQueue.take();
            free(oldEntry.getInCachePosition());
        }
    }

    private DataPayload getEntry(AsciiString key) {
        int pos = find(key);

        return (pos == NULL) ? null : entries[pos];
    }

    @Override
    public DataPayload get(AsciiString key) {
        DataPayload entry = getEntry(key);
        assert entry == null || key.equals(entry.getKey());
        return entry;
    }

    @Override
    public void deactivate(DataPayload entry) {
        assert find(entry.getKey()) != NULL;

        displaceOldestInactiveOrderIfQueueFull();
        inactiveDataQueue.put(entry);
    }

    @Override
    public int capacity() {
        return entries.length;
    }

    @Override
    public int size() {
        return count;
    }
}
