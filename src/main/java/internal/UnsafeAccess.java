package internal;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

public class UnsafeAccess {

    public static final Unsafe UNSAFE;

    static {
        Unsafe unsafe = null;
        try {
            PrivilegedExceptionAction<Unsafe> action = new PrivilegedExceptionAction<Unsafe>() {
                @Override
                public Unsafe run() throws Exception {
                    Field f = Unsafe.class.getDeclaredField("theUnsafe");
                    f.setAccessible(true);
                    return (Unsafe) f.get(null);
                }
            };
            unsafe = AccessController.doPrivileged(action);
        } catch (final Exception ex) {
            throw (RuntimeException)ex;
        }

        UNSAFE = unsafe;
    }

    /**
     * This method is completely unsafe, so please use with care.
     */
    public static void copyMemory(byte[] srcArray, long srcOffset, byte[] dstArray, long dstOffset, int length) {
        if (length >= 64) {
            // call mem copy for quite long array
            UNSAFE.copyMemory(srcArray, srcOffset, dstArray, dstOffset, length);
            return;
        }

        while (length >= 8){
            long bytes = UNSAFE.getLong(srcArray, srcOffset);
            UNSAFE.putLong(dstArray, dstOffset, bytes);
            length -= 8;
            dstOffset += 8;
            srcOffset += 8;
        }

        if (length >= 4) {
            int bytes = UNSAFE.getInt(srcArray, srcOffset);
            UNSAFE.putInt(dstArray, dstOffset, bytes);
            length -= 4;
            dstOffset += 4;
            srcOffset += 4;
        }

        if (length >= 2) {
            short bytes = UNSAFE.getShort(srcArray, srcOffset);
            UNSAFE.putShort(dstArray, dstOffset, bytes);
            length -= 2;
            dstOffset += 2;
            srcOffset += 2;
        }

        if (length == 1) {
            byte bytes = UNSAFE.getByte(srcArray, srcOffset);
            UNSAFE.putByte(dstArray, dstOffset, bytes);
        }
    }
}
