package hashing;

import sun.misc.Unsafe;

public class vhHashCodeComputer extends VarHandleHashCodeComputer {

    public static final vhHashCodeComputer INSTANCE = new vhHashCodeComputer();

    private vhHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] a, Unsafe unsafeAccess, long address, int off, int len) {
        int h = 0x9E3779B9;
        int i = 0;
        for (; i + 8 <= len; i += 8) {
            long v = (long) LONG_LE.get(a, off + i);
//            h ^= (int) v ^ (int) (v >>> 32); h = mix32(h);
            h ^= (int) v; h = mix32(h);
            h ^= (int) (v >>> 32); h = mix32(h);
        }
        if (i + 4 <= len) {
            int v = (int) INT_LE.get(a, off + i);
            h ^= v; h = mix32(h);
            i += 4;
        }
        int t = 0;
        int rem = len - i;
        if (rem > 0) t ^= (a[off + i] & 0xFF);
        if (rem > 1) t ^= (a[off + i + 1] & 0xFF) << 8;
        if (rem > 2) t ^= (a[off + i + 2] & 0xFF) << 16;
        h ^= t; h = mix32(h);
        return fmix32(h, len);
    }

//    @Override
//    protected int hashCode(long key) {
//        return (int) (key ^ (key >>> 32));
//    }


    private static int mix32(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        return x * 0x846ca68b;
    }

    private static int fmix32(int x, int len) {
        x ^= len;
        x ^= x >>> 16;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        x ^= x >>> 16;
        return x;
    }
}
