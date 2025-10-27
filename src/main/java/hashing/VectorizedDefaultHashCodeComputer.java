package hashing;

import jdk.incubator.vector.*;
import sun.misc.Unsafe;

import static jdk.incubator.vector.VectorOperators.ADD;

public class VectorizedDefaultHashCodeComputer extends HashCodeComputer {
    public static final VectorizedDefaultHashCodeComputer INSTANCE = new VectorizedDefaultHashCodeComputer();
    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_256;
    private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_256;

    private static final int[] POWERS_OF_31_BACKWARDS = new int[33];
    static {
        POWERS_OF_31_BACKWARDS[POWERS_OF_31_BACKWARDS.length - 1] = 1;
        for (int i = POWERS_OF_31_BACKWARDS.length - 2; i >= 0; --i) {
            POWERS_OF_31_BACKWARDS[i] = 31 * POWERS_OF_31_BACKWARDS[i + 1];
        }
    }

    private VectorizedDefaultHashCodeComputer() {
    }

    @Override
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, int off, int length) {
        IntVector next = IntVector.broadcast(INT_SPECIES, POWERS_OF_31_BACKWARDS[33 - 9]);
        var coefficients = IntVector.fromArray(INT_SPECIES, POWERS_OF_31_BACKWARDS, 33 - 8);
        IntVector acc = IntVector.zero(INT_SPECIES);
        int i;
        for (i = length; i - INT_SPECIES.length() >= 0; i -= INT_SPECIES.length()) {
            VectorMask<Byte> mask = BYTE_SPECIES.indexInRange(0, INT_SPECIES.length());
            ByteVector values = ByteVector.fromArray(BYTE_SPECIES, input, off + i - INT_SPECIES.length(), mask);
            IntVector iValues = (IntVector) values.convertShape(VectorOperators.B2I, INT_SPECIES, 0);
            acc = acc.add(coefficients.mul(iValues));
            coefficients = coefficients.mul(next);
        }
        if (i > 0) {
            VectorMask<Byte> mask = BYTE_SPECIES.indexInRange(0, length);
            ByteVector values = ByteVector.fromArray(BYTE_SPECIES, input, off, mask);
            IntVector iValues = (IntVector) values.convertShape(VectorOperators.B2I, INT_SPECIES, 0);
            acc = acc.add(coefficients.mul(iValues));
            coefficients = coefficients.mul(next);
        }

        return acc.reduceLanes(ADD) + coefficients.lane(7);
    }

    @Override
    protected int hashCode(long key) {
        return (int) (key ^ (key >>> 32));
    }
}
