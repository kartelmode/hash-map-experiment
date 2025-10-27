package hashing;

import internal.AsciiString;
import sun.misc.Unsafe;

import static internal.UnsafeAccess.UNSAFE;

public abstract class HashCodeComputer {
    public int modPowerOfTwoHashCode(AsciiString key, int mod) {
        return modPowerOfTwoHashCode(hashCode(key), mod);
    }

    public int hashCode(AsciiString key) {
        return hashCode(key.getArray(), UNSAFE, key.getAddress(), 0, key.getLength());
    }

    protected abstract int hashCode(byte[] array, Unsafe unsafeAccess, long address, int offset, int length);

//    protected abstract int hashCode(long key);

//    public int modPowerOfTwoHashCode(long key, int mod) {
//        return computeModPowerOfTwoHashCode(hashCode(key), mod);
//    }

    public int modPowerOfTwoHashCode(int key, int mod) {
        return computeModPowerOfTwoHashCode(key, mod);
    }

    public static int computeModPowerOfTwoHashCode(int key, int mod) {
        assert Integer.bitCount(mod) == 1;

        if (key == Integer.MIN_VALUE)
            return (1);

        if (key < 0)
            key = -key;

        return (key & (mod - 1));
    }
}
