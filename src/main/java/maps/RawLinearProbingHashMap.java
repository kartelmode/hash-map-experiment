package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;
import internal.FixedSizeQueue;

import static internal.UnsafeAccess.UNSAFE;
import static internal.UnsafeAccess.copyMemory;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

public class RawLinearProbingHashMap implements Cache {
    public static final int MIN_CAPACITY = 16;
    public static final int DEFAULT_LOAD_FACTOR = 50;
    public static final int DEFAULT_MAX_KEY_LENGTH = 64;
    protected final int loadFactor;
    protected static final int NULL = Integer.MIN_VALUE;
    protected final HashCodeComputer hashCodeComputer;

    protected int count = 0;
    protected long collisions = 0;
    protected int maxKeyLength = 0;

    protected DataPayload[] entries;
    protected int lengthMask;

    // array keys is divided by blocks length of maxKeyLength. Each block contains 4 bytes for length of the key and key itself in [4; maxKeyLength) range
    protected byte[] keys;

    protected final FixedSizeQueue<DataPayload> inactiveDataQueue; // contains most recent inactive orders

    public RawLinearProbingHashMap(int activeDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer) {
        this(activeDataCount, maxInactiveDataCount, hashCodeComputer, DEFAULT_LOAD_FACTOR, DEFAULT_MAX_KEY_LENGTH);
    }

    public RawLinearProbingHashMap(int activeDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer, int loadFactor, int maxKeyLength) {
        if (activeDataCount < MIN_CAPACITY)
            activeDataCount = MIN_CAPACITY;

        if (Integer.bitCount(activeDataCount) != 1) {
            throw new IllegalArgumentException("activeDataCount must be a power of 2");
        }

        if (Integer.bitCount(maxKeyLength) != 1) {
            throw new IllegalArgumentException("maxKeyLength must be a power of 2");
        }

        if (loadFactor <= 0 || loadFactor > 100) {
            throw new IllegalArgumentException("loadFactor must be between 0 and 100");
        }

        if (maxKeyLength < 32) {
            throw new IllegalArgumentException("maxKeyLength must be at least 32");
        }

        this.loadFactor = loadFactor;
        this.maxKeyLength = maxKeyLength;

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

    private int getKeyId(int idx) {
        return idx * maxKeyLength;
    }

    private int getKeyLength(int idx) {
        return UNSAFE.getInt(keys, ARRAY_BYTE_BASE_OFFSET + (long) idx * maxKeyLength);
    }

    private boolean compareKeys(AsciiString key, int idx) {
        int length = getKeyLength(idx);
        if (key.getLength() != length) {
            return false;
        }
        byte[] keyArray = key.getArray();
        long keyAddress = key.getAddress();

        int i = 0;
        long offset = ARRAY_BYTE_BASE_OFFSET + getKeyId(idx) + 4;
        while (i + 8 <= length) {
            long lhs = UNSAFE.getLong(keyArray, keyAddress + i);
            long rhs = UNSAFE.getLong(keys, offset + i);

            if (lhs != rhs) {
                return false;
            }
            i += 8;
        }
        while (i + 4 <= length) {
            int lhs = UNSAFE.getInt(keyArray, keyAddress + i);
            int rhs = UNSAFE.getInt(keys, offset + i);

            if (lhs != rhs) {
                return false;
            }
            i += 4;
        }

        while (i + 2 <= length) {
            short lhs = UNSAFE.getShort(keyArray, keyAddress + i);
            short rhs = UNSAFE.getShort(keys, offset + i);

            if (lhs != rhs) {
                return false;
            }
            i += 2;
        }

        return i == length || UNSAFE.getByte(keyArray, keyAddress + i) == UNSAFE.getByte(keys, offset + i);
    }

    private int nextFree(int idx) {
        int attempts = 0;
        while (attempts < entries.length && isFilled(idx)) {
            idx = (idx + 1) & lengthMask;
            attempts++;
        }
        collisions += (attempts > 0 ? 1 : 0);
        return idx;
    }

    private void copyKey(AsciiString key, int idx) {
        byte[] keyArray = key.getArray();
        long keyAddress = key.getAddress();
        int length = key.getLength();

        UNSAFE.putInt(keys, ARRAY_BYTE_BASE_OFFSET + getKeyId(idx), length);
        copyMemory(keyArray, keyAddress, keys, ARRAY_BYTE_BASE_OFFSET + getKeyId(idx) + 4, length);
    }

    private void setEntry(DataPayload entry, AsciiString key, int idx) {
        entry.setInCachePosition(idx);
        entries[idx] = entry;
        copyKey(key, idx);
    }

    protected void allocTable(int cap) {
        entries = new DataPayload[cap];
        keys = new byte[cap * maxKeyLength];
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
        int hidx = nextFree(hashIndex(entry.getKey()));

        count++;
        setEntry(entry, entry.getKey(), hidx);
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
            AsciiString key = entry.getKey();
            int hidx = hashIndex(key);
            if ((curIdx < hidx && (hidx <= deletedIdx || deletedIdx <= curIdx)) ||
                    (hidx <= deletedIdx && deletedIdx <= curIdx)) {
                setEntry(entry, key, deletedIdx);
                entries[curIdx] = null;
                deletedIdx = curIdx;
            }
        }
    }

    protected boolean keyEquals(AsciiString a, int idx) {
        if (a == null) {
            return isEmpty(idx);
        }
        return compareKeys(a, idx);
    }

    protected int find(AsciiString key) {
        return (find(hashIndex(key), key));
    }

    protected int find(int hidx, AsciiString key) {
        int attempts = 0;
        for (; attempts < entries.length && isFilled(hidx); hidx = (hidx + 1) & lengthMask, attempts++) {
            if (keyEquals(key, hidx)) {
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
        AsciiString key = entry.getKey();
        int hidx = hashIndex(key);
        int idx = find(hidx, key);

        if (idx != NULL) {
            return false;
        }

        if (count * 100 >= entries.length * loadFactor) {
            resizeTable(entries.length * 2);
            hidx = hashIndex(key);
        }

        putEntry(entry, key, hidx);
        return true;
    }

    @Override
    public DataPayload get(AsciiString key) {
        int pos = find(key);

        return (pos == NULL) ? null : entries[pos];
    }

    protected void putEntry(DataPayload entry, AsciiString key, int hidx) {
        hidx = nextFree(hidx);
        count++;
        setEntry(entry, key, hidx);
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
    public int capacity() {
        return entries.length;
    }

    @Override
    public int size () {
        return count;
    }
}
