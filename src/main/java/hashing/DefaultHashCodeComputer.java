package hashing;

import sun.misc.Unsafe;

public class DefaultHashCodeComputer extends HashCodeComputer {
    public static final DefaultHashCodeComputer INSTANCE = new DefaultHashCodeComputer();

    private DefaultHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, int off, int length) {
        int hash = 0;
        for (int i = 0; i < length; i++) {
            hash = 31 * hash + unsafeAccess.getByte(input, address + off + i);
        }
        return hash;
    }

//    @Override
//    protected int hashCode(long key) {
//        return (int) (key ^ (key >>> 32));
//    }
}
