package hashing;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;
import sun.misc.Unsafe;
import jdk.incubator.vector.IntVector;

import java.nio.ByteBuffer;

import static jdk.incubator.vector.VectorOperators.ADD;

public class VectorizedDefaultHashCodeComputer extends HashCodeComputer {
    public static VectorizedDefaultHashCodeComputer INSTANCE = new VectorizedDefaultHashCodeComputer();
    private static final VectorSpecies<Byte> I256 = ByteVector.SPECIES_256;

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
    protected int hashCode(byte[] input, Unsafe unsafeAccess, long address, long off, long length) {
//        var next = ByteVector.broadcast(I256, POWERS_OF_31_BACKWARDS[33 - 9]);
////        var coefficients = ByteVector.fromArray(I256, POWERS_OF_31_BACKWARDS, 33 - 8);
//        var acc = ByteVector.zero(I256);
//        for (int i = (int) length; i - I256.length() >= 0; i -= I256.length()) {
//            acc = acc.add(coefficients.mul(ByteVector.fromArray(I256, input, (int)off + i - I256.length())));
//            coefficients = coefficients.mul(next);
//        }
//        return acc.reduceLanes(ADD) + coefficients.lane(7);
        return 0;
    }

    @Override
    protected int hashCode(long key) {
        return (int) (key ^ (key >>> 32));
    }
}
