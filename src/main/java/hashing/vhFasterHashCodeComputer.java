package hashing;

import sun.misc.Unsafe;

public class vhFasterHashCodeComputer extends VarHandleHashCodeComputer{
    public static final vhFasterHashCodeComputer INSTANCE = new vhFasterHashCodeComputer();

    private vhFasterHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] input, Unsafe a, long aa, int off, int length) {
        int hash = 0, i;

        for (i = 0; i + 4 < length; i += 4) {
            hash = 71 * hash + (int) INT_LE.get(input, off + i);
        }

        if (i + 2 < length) {
            hash = 71 * hash + (short)SHORT_LE.get(input, off + i);
            i += 2;
        }

        if (i + 1 < length) {
            hash = 71 * hash + input[off + i];
        }
        return hash;
    }

//    @Override
//    protected int hashCode(long key) {
//        return (int) (key ^ (key >>> 32));
//    }

}
