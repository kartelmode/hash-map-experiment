package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataWrapper;
import internal.ObjectPool;

public class RobinHoodHashMap extends LinearProbingHashMap {
    private int[] probeSeqLength;

    RobinHoodHashMap(int activeDataCount, int maxInactiveDataCount, ObjectPool<DataWrapper> objectPool, HashCodeComputer hashCodeComputer) {
        super(activeDataCount, maxInactiveDataCount, objectPool, hashCodeComputer);
    }

    RobinHoodHashMap(int activeDataCount, int maxInactiveDataCount, ObjectPool<DataWrapper> objectPool, HashCodeComputer hashCodeComputer, int loadFactor) {
        super(activeDataCount, maxInactiveDataCount, objectPool, hashCodeComputer, loadFactor);
    }

    @Override
    protected void allocTable(int cap) {
        probeSeqLength = new int[cap];
        super.allocTable(cap);
    }

    @Override
    protected void putNewNoSpaceCheck(DataWrapper entry) {
        int hidx = hashIndex(entry.getAsciiString());
        putEntry(entry, hidx);
    }

    @Override
    protected void free(int idx) {
        count--;
        entries[idx].setInCachePosition(-1);
        entries[idx] = null;
        probeSeqLength[idx] = 0;
        int nidx = (idx + 1) & lengthMask;

        while (isFilled(nidx) && probeSeqLength[nidx] > 0) {
            probeSeqLength[idx] = probeSeqLength[nidx] - 1;
            entries[idx] = entries[nidx];
            entries[idx].setInCachePosition(idx);

            entries[nidx] = null;
            probeSeqLength[nidx] = -1;

            nidx = (nidx + 1) & lengthMask;
            idx = (idx + 1) & lengthMask;
        }
    }

    @Override
    protected void putEntry(DataWrapper entry, int hidx) {
        DataWrapper tmp;

        int currentProbeSeqLength = 0;
        while (isFilled(hidx)) {
            if (currentProbeSeqLength > probeSeqLength[hidx]) {
                entry.setInCachePosition(hidx);
                tmp = entries[hidx];
                entries[hidx] = entry;
                entry = tmp;

                currentProbeSeqLength ^= probeSeqLength[hidx]; // swap values without temp variable
                probeSeqLength[hidx] ^= currentProbeSeqLength;
                currentProbeSeqLength ^= probeSeqLength[hidx];
            }
            hidx = (hidx + 1) & lengthMask;
            currentProbeSeqLength++;
        }
        collisions += (currentProbeSeqLength > 0 ? 1 : 0);
        count++;
        entry.setInCachePosition(hidx);
        entries[hidx] = entry;
        probeSeqLength[hidx] = currentProbeSeqLength;
    }

    @Override
    protected int find(int hidx, AsciiString key) {
        int currentProbeSeqLength = 0;

        for (;isFilled(hidx) && currentProbeSeqLength <= probeSeqLength[hidx]; hidx = (hidx + 1) & lengthMask, currentProbeSeqLength++) {
            if (keyEquals(entries[hidx].getAsciiString(), key)) {
                return hidx;
            }
        }
        return NULL;
    }

    @Override
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
}
