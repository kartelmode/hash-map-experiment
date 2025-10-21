package hashing;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public abstract class VarHandleHashCodeComputer extends HashCodeComputer {
    // View a byte[] as long[] and int[]
    protected static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    protected static final VarHandle INT_LE  = MethodHandles.byteArrayViewVarHandle(int[].class,  ByteOrder.LITTLE_ENDIAN);
    protected static final VarHandle SHORT_LE  = MethodHandles.byteArrayViewVarHandle(short[].class,  ByteOrder.LITTLE_ENDIAN);
}
