package hashing;

import sun.misc.Unsafe;

public class NativeHashCodeComputer extends  HashCodeComputer {
    public static final NativeHashCodeComputer INSTANCE = new NativeHashCodeComputer();
    private static final int M2 = 0x7A646E4D;

    private NativeHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, int off, int length) {
        long hash = 0;
        int i = 0;
        for (i = 0; i + 4 <= length; i += 4) {
            hash = M2 * hash + unsafeAccess.getInt(input, address + off + i);
        }
        hash *= M2;
        int value = 0;
        if (i + 2 <= length) {
            value += unsafeAccess.getShort(input, address + off + i);
            i += 2;
        }
        if (i < length) {
            value <<= 8;
            value += unsafeAccess.getByte(input, address + off + i);
        }
        if (value != 0) {
            hash += value;
            hash *= M2;
        }
        return (int)hash ^ (int)(hash >>> 25);
    }

//    @Override
//    protected int hashCode(long key) {
//        long h = (key >>> 32) + (key & 0xFFFFFFFF00000000L) * M2;
//        return (int) h ^ (int) (h >>> 25);
//    }
}
