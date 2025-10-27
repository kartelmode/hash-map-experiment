package hashing;

import sun.misc.Unsafe;

public class XxHashCodeComputer extends HashCodeComputer {
    public static final XxHashCodeComputer INSTANCE = new XxHashCodeComputer();

    private XxHashCodeComputer() {
    }

    // Primes if treated as unsigned
    private static final long P1 = -7046029288634856825L;
    private static final long P2 = -4417276706812531889L;
    private static final long P3 = 1609587929392839161L;
    private static final long P4 = -8796714831421723037L;
    private static final long P5 = 2870177450012600261L;

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, int off, int length) {
        long hash;
        long remaining = length;

        if (remaining >= 32) {
            long v1 = P1 + P2;
            long v2 = P2;
            long v3 = 0;
            long v4 = -P1;

            do {
                v1 += unsafeAccess.getLong(input, address + off) * P2;
                v1 = Long.rotateLeft(v1, 31);
                v1 *= P1;

                v2 += unsafeAccess.getLong(input, address + off + 8) * P2;
                v2 = Long.rotateLeft(v2, 31);
                v2 *= P1;

                v3 += unsafeAccess.getLong(input, address + off + 16) * P2;
                v3 = Long.rotateLeft(v3, 31);
                v3 *= P1;

                v4 += unsafeAccess.getLong(input, address + off + 24) * P2;
                v4 = Long.rotateLeft(v4, 31);
                v4 *= P1;

                off += 32;
                remaining -= 32;
            } while (remaining >= 32);

            hash = Long.rotateLeft(v1, 1)
                    + Long.rotateLeft(v2, 7)
                    + Long.rotateLeft(v3, 12)
                    + Long.rotateLeft(v4, 18);

            v1 *= P2;
            v1 = Long.rotateLeft(v1, 31);
            v1 *= P1;
            hash ^= v1;
            hash = hash * P1 + P4;

            v2 *= P2;
            v2 = Long.rotateLeft(v2, 31);
            v2 *= P1;
            hash ^= v2;
            hash = hash * P1 + P4;

            v3 *= P2;
            v3 = Long.rotateLeft(v3, 31);
            v3 *= P1;
            hash ^= v3;
            hash = hash * P1 + P4;

            v4 *= P2;
            v4 = Long.rotateLeft(v4, 31);
            v4 *= P1;
            hash ^= v4;
            hash = hash * P1 + P4;
        } else {
            hash = P5;
        }

        hash += length;

        while (remaining >= 8) {
            long k1 = unsafeAccess.getLong(input, address + off);
            k1 *= P2;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= P1;
            hash ^= k1;
            hash = Long.rotateLeft(hash, 27) * P1 + P4;
            off += 8;
            remaining -= 8;
        }

        if (remaining >= 4) {
            hash ^= unsafeAccess.getInt(input, address + off) * P1;
            hash = Long.rotateLeft(hash, 23) * P2 + P3;
            off += 4;
            remaining -= 4;
        }

        while (remaining != 0) {
            hash ^= unsafeAccess.getByte(input, address + off) * P5;
            hash = Long.rotateLeft(hash, 11) * P1;
            --remaining;
            ++off;
        }

        return Long.hashCode(finalize(hash));
    }

    private static long finalize(long hash) {
        hash ^= hash >>> 33;
        hash *= P2;
        hash ^= hash >>> 29;
        hash *= P3;
        hash ^= hash >>> 32;
        return hash;
    }

    @Override
    public int hashCode(long key) {
        long hash = 8;

        long k1 = key;
        k1 *= P2;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= P1;
        hash ^= k1;
        hash = Long.rotateLeft(hash, 27) * P1 + P4;
        return Long.hashCode(finalize(hash));
    }
}
