package hashing;

import sun.misc.Unsafe;

public class MetroHashCodeComputer extends HashCodeComputer {
    public static MetroHashCodeComputer INSTANCE = new MetroHashCodeComputer();

    private MetroHashCodeComputer() {
    }

    //primes
    private static final long k0 = 0xD6D018F5L;
    private static final long k1 = 0xA2AA033BL;
    private static final long k2 = 0x62992FC1L;
    private static final long k3 = 0x30BC5B29L;

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, long off, long length) {
        long remaining = length;

        long h = k2 * k0;

        if (length >= 32) {
            long v0 = h;
            long v1 = h;
            long v2 = h;
            long v3 = h;

            do {
                v0 += unsafeAccess.getLong(input, address + off) * k0;
                v0 = Long.rotateRight(v0, 29) + v2;
                v1 += unsafeAccess.getLong(input, address + off + 8) * k1;
                v1 = Long.rotateRight(v1, 29) + v3;
                v2 += unsafeAccess.getLong(input, address + off + 16) * k2;
                v2 = Long.rotateRight(v2, 29) + v0;
                v3 += unsafeAccess.getLong(input, address + off + 24) * k3;
                v3 = Long.rotateRight(v3, 29) + v1;

                off += 32;
                remaining -= 32;
            } while (remaining >= 32);

            v2 ^= Long.rotateRight(((v0 + v3) * k0) + v1, 37) * k1;
            v3 ^= Long.rotateRight(((v1 + v2) * k1) + v0, 37) * k0;
            v0 ^= Long.rotateRight(((v0 + v2) * k0) + v3, 37) * k1;
            v1 ^= Long.rotateRight(((v1 + v3) * k1) + v2, 37) * k0;

            h += v0 ^ v1;
        }

        if (remaining >= 16) {
            long v0 = h + (unsafeAccess.getLong(input, address + off) * k2);
            v0 = Long.rotateRight(v0, 29) * k3;
            long v1 = h + (unsafeAccess.getLong(input, address + off + 8) * k2);
            v1 = Long.rotateRight(v1, 29) * k3;
            v0 ^= Long.rotateRight(v0 * k0, 21) + v1;
            v1 ^= Long.rotateRight(v1 * k3, 21) + v0;
            h += v1;

            off += 16;
            remaining -= 16;
        }

        if (remaining >= 8) {
            h += unsafeAccess.getLong(input, address + off) * k3;
            h ^= Long.rotateRight(h, 55) * k1;

            off += 8;
            remaining -= 8;
        }

        if (remaining >= 4) {
            h += unsafeAccess.getInt(input, address + off) * k3;
            h ^= Long.rotateRight(h, 26) * k1;

            off += 4;
            remaining -= 4;
        }

        if (remaining >= 2) {
            h += unsafeAccess.getShort(input, address + off) * k3;
            h ^= Long.rotateRight(h, 48) * k1;

            off += 2;
            remaining -= 2;
        }

        if (remaining >= 1) {
            h += unsafeAccess.getByte(input, address + off) * k3;
            h ^= Long.rotateRight(h, 37) * k1;
        }

        return Long.hashCode(finalize(h));
    }

    private static long finalize(long h) {
        h ^= Long.rotateRight(h, 28);
        h *= k0;
        h ^= Long.rotateRight(h, 29);
        return h;
    }

    @Override
    public int hashCode(long key) {
        long h = key * k3;
        h ^= Long.rotateRight(h, 55) * k1;

        return Long.hashCode(finalize(h));
    }
}
