package hashing;

import sun.misc.Unsafe;

public class UnrolledDefaultHashCodeComputer extends HashCodeComputer {
    public static UnrolledDefaultHashCodeComputer INSTANCE = new UnrolledDefaultHashCodeComputer();

    private UnrolledDefaultHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, int off, int length) {
        int hash = 0;
        int i = 0;
        for (i = 0; i + 4 < length; i += 4) {
            int a = unsafeAccess.getByte(input, address + off + i);
            int b = unsafeAccess.getByte(input, address + off + i + 1);
            int c = unsafeAccess.getByte(input, address + off + i + 2);
            int d = unsafeAccess.getByte(input, address + off + i + 3);
            hash = 31 * 31 * 31 * 31 * hash +
                    31 * 31 * 31 * a +
                    31 * 31 * b +
                    31 * c +
                    d;
        }

        while (i < length) {
            hash = 31 * hash + unsafeAccess.getByte(input, address + off + i);
            i++;
        }
        return hash;
    }

    @Override
    protected int hashCode(long key) {
        return (int) (key ^ (key >>> 32));
    }
}
