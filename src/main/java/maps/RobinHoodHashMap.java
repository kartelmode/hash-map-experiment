package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;

public final class RobinHoodHashMap extends LinearProbingHashMap {
    private int[] probeSeqLength;

    public RobinHoodHashMap(int activeDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer) {
        super(activeDataCount, maxInactiveDataCount, hashCodeComputer);
    }

    public RobinHoodHashMap(int activeDataCount, int maxInactiveDataCount, HashCodeComputer hashCodeComputer, int loadFactor) {
        super(activeDataCount, maxInactiveDataCount, hashCodeComputer, loadFactor);
    }

    @Override
    protected void allocTable(int cap) {
        probeSeqLength = new int[cap];
        super.allocTable(cap);
    }

    @Override
    protected void free(int idx) {
        count--;
        entries[idx].setInCachePosition(-1);
        entries[idx] = null;
        probeSeqLength[idx] = 0;
        int lengthMask = entries.length - 1;
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
    protected void putEntry(DataPayload entry, int hidx) {
        DataPayload tmp;
        int lengthMask = entries.length - 1;

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
        count++;
        entry.setInCachePosition(hidx);
        entries[hidx] = entry;
        probeSeqLength[hidx] = currentProbeSeqLength;
    }

    @Override
    protected int find(int hidx, AsciiString key) {
        int currentProbeSeqLength = 0;
        int lengthMask = entries.length - 1;

        for (;isFilled(hidx) && currentProbeSeqLength <= probeSeqLength[hidx]; hidx = (hidx + 1) & lengthMask, currentProbeSeqLength++) {
            if (keyEquals(entries[hidx].getKey(), key)) {
                return hidx;
            }
        }
        return NULL;
    }

    @Override
    public boolean putIfEmpty(DataPayload entry) {
        if (count >= threshold) {
            resizeTable(entries.length * 2);
        }

        int hidx = hashIndex(entry.getKey());
        AsciiString key = entry.getKey();
        int currentProbeSeqLength = 0;
        int lengthMask = entries.length - 1;

        for (; isFilled(hidx) && currentProbeSeqLength <= probeSeqLength[hidx]; hidx = (hidx + 1) & lengthMask, currentProbeSeqLength++) {
            if (keyEquals(entries[hidx].getKey(), key)) {
                return false;
            }
        }

        DataPayload tmp;
        int lengthMask1 = entries.length - 1;

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
            hidx = (hidx + 1) & lengthMask1;
            currentProbeSeqLength++;
        }
        count++;
        entry.setInCachePosition(hidx);
        entries[hidx] = entry;
        probeSeqLength[hidx] = currentProbeSeqLength;
        return true;
    }

}
