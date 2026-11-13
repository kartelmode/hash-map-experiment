package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;
import internal.FixedSizeQueue;

public final class StoredHashLinearProbingHashMap implements Cache {
    public static final int MIN_CAPACITY = 16;
    public static final float DEFAULT_LOAD_FACTOR = 0.5f;

    private static final int EMPTY = 0;
    private static final int NULL = Integer.MIN_VALUE;

    private final HashCodeComputer hashCodeComputer;
    private final float loadFactor;
    private final FixedSizeQueue<DataPayload> inactiveDataQueue; // contains most recent inactive orders

    private int[] hashes;          // stored hash per slot, EMPTY for a free slot
    private DataPayload[] entries; // payload per slot, null for a free slot
    private int mask;
    private int threshold;
    private int count;

    /** Legacy sizing, like {@link LinearProbingHashMap}: twice the larger of the active and inactive counts. */
    public StoredHashLinearProbingHashMap(int activeDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer) {
        this(hashCodeComputer, LinearProbingHashMap.defaultTableCapacity(activeDataCount, maxInactiveDataCount),
                maxInactiveDataCount, DEFAULT_LOAD_FACTOR);
    }

    private StoredHashLinearProbingHashMap(HashCodeComputer hashCodeComputer, int tableCapacity, int maxInactiveDataCount,
                                           float maxLoadFactor) {
        if (tableCapacity < MIN_CAPACITY || Integer.bitCount(tableCapacity) != 1)
            throw new IllegalArgumentException("tableCapacity must be a power of 2 and >= " + MIN_CAPACITY + ": " + tableCapacity);
        if (!(maxLoadFactor > 0 && maxLoadFactor < 1))
            throw new IllegalArgumentException("maxLoadFactor must be in (0, 1): " + maxLoadFactor);

        this.hashCodeComputer = hashCodeComputer;
        this.loadFactor = maxLoadFactor;
        this.inactiveDataQueue = new FixedSizeQueue<>(maxInactiveDataCount);
        allocTable(tableCapacity);
    }

    /** A map with exactly {@code tableCapacity} slots, resized only when the load reaches {@code maxLoadFactor}. */
    public static StoredHashLinearProbingHashMap withTableCapacity(int tableCapacity, int maxInactiveDataCount,
                                                                   HashCodeComputer hashCodeComputer, float maxLoadFactor) {
        return new StoredHashLinearProbingHashMap(hashCodeComputer, tableCapacity, maxInactiveDataCount, maxLoadFactor);
    }

    private void allocTable(int capacity) {
        hashes = new int[capacity];
        entries = new DataPayload[capacity];
        mask = capacity - 1;
        threshold = Math.max(1, Math.min(capacity - 1, (int) (capacity * loadFactor)));
    }

    /** The key's hash as stored: never {@link #EMPTY}. */
    private int storedHash(AsciiString key) {
        final int hash = hashCodeComputer.hashCode(key);
        return (hash == EMPTY) ? 1 : hash;
    }

    private int homeSlot(int hash) {
        return HashCodeComputer.computeModPowerOfTwoHashCode(hash, mask + 1);
    }

    /** @return the slot holding {@code key}, or {@link #NULL} */
    private int find(int hash, AsciiString key) {
        final int[] hs = hashes;
        final int m = mask;
        for (int slot = homeSlot(hash); ; slot = (slot + 1) & m) {
            final int h = hs[slot];
            if (h == EMPTY)
                return NULL;
            if (h == hash && entries[slot].getKey().equals(key))
                return slot;
        }
    }

    @Override
    public boolean putIfEmpty(DataPayload entry) {
        if (count >= threshold)
            resizeTable((mask + 1) * 2);

        final AsciiString key = entry.getKey();
        final int hash = storedHash(key);
        final int[] hs = hashes;
        final int m = mask;

        int slot = homeSlot(hash);
        for (int h; (h = hs[slot]) != EMPTY; slot = (slot + 1) & m) {
            if (h == hash && entries[slot].getKey().equals(key))
                return false;
        }

        hs[slot] = hash;
        entries[slot] = entry;
        entry.setInCachePosition(slot);
        count++;
        return true;
    }

    @Override
    public DataPayload get(AsciiString key) {
        final int slot = find(storedHash(key), key);
        return (slot == NULL) ? null : entries[slot];
    }

    @Override
    public void deactivate(DataPayload entry) {
        assert find(storedHash(entry.getKey()), entry.getKey()) != NULL;

        if (inactiveDataQueue.isFull())
            free(inactiveDataQueue.take().getInCachePosition());
        inactiveDataQueue.put(entry);
    }

    private void free(int slot) {
        final int[] hs = hashes;
        final DataPayload[] es = entries;
        final int m = mask;

        es[slot].setInCachePosition(-1);
        count--;

        int hole = slot;
        for (int cur = (slot + 1) & m; ; cur = (cur + 1) & m) {
            final int h = hs[cur];
            if (h == EMPTY)
                break;
            final int probeDistance = (cur - homeSlot(h)) & m;
            final int holeDistance = (cur - hole) & m;
            if (probeDistance >= holeDistance) { // the hole lies in [home, cur): the entry may move into it
                final DataPayload moved = es[cur];
                hs[hole] = h;
                es[hole] = moved;
                moved.setInCachePosition(hole);
                hole = cur;
            }
        }
        hs[hole] = EMPTY;
        es[hole] = null;
    }

    /** Rehashes from the stored hashes: no key is read and no hash is recomputed. */
    private void resizeTable(int newCapacity) {
        final int[] oldHashes = hashes;
        final DataPayload[] oldEntries = entries;

        allocTable(newCapacity);
        final int[] hs = hashes;
        final DataPayload[] es = entries;
        final int m = mask;

        for (int i = 0; i < oldHashes.length; i++) {
            final int h = oldHashes[i];
            if (h == EMPTY)
                continue;
            int slot = homeSlot(h);
            while (hs[slot] != EMPTY)
                slot = (slot + 1) & m;
            hs[slot] = h;
            es[slot] = oldEntries[i];
            oldEntries[i].setInCachePosition(slot);
        }
    }

    @Override
    public int capacity() {
        return mask + 1;
    }

    @Override
    public int size() {
        return count;
    }
}
