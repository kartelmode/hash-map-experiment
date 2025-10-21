package hashing;

import sun.misc.Unsafe;

public class FasterHashCodeComputer extends HashCodeComputer {
    public static FasterHashCodeComputer INSTANCE = new FasterHashCodeComputer();

    private FasterHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, int off, int length) {
        int hash = 0, i;
        for (i = 0; i + 4 < length; i += 4) {
            hash = 71 * hash + unsafeAccess.getInt(input, address + off + i);
        }

        if (i + 2 < length) {
            hash = 71 * hash + unsafeAccess.getShort(input, address + off + i);
            i += 2;
        }

        if (i + 1 < length) {
            hash = 71 * hash + unsafeAccess.getByte(input, address + off + i);
        }
        return hash;
    }

    @Override
    protected int hashCode(long key) {
        return (int) (key ^ (key >>> 32));
    }
}
