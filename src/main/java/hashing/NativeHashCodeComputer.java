package hashing;

import sun.misc.Unsafe;

public class NativeHashCodeComputer extends  HashCodeComputer {
    public static final NativeHashCodeComputer INSTANCE = new NativeHashCodeComputer();
    private static final int M2 = 0x7A646E4D;

    private NativeHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, long off, long length) {
        long hash = 0;
        int i = 0;
        for (i = 0; i < length; i += 4) {
            hash = M2 * hash + unsafeAccess.getInt(input, address + off + i);
        }
        hash *= M2;
        return (int)hash ^ (int)(hash >>> 25);
    }

    @Override
    protected int hashCode(long key) {
        long h = (key >>> 32) + (key & 0xFFFFFFFF00000000L) * M2;
        return (int) h ^ (int) (h >>> 25);
    }
}
