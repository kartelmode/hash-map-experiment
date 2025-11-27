package internal;

import hashing.NativeHashCodeComputer;

import static internal.UnsafeAccess.UNSAFE;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

public final class AsciiString {
    private static final byte[] MIN_INTEGER_VALUE = String.valueOf(Integer.MIN_VALUE).getBytes();

    private byte[] array;
    private int length;
    private final long address;

    public AsciiString(final CharSequence value) {
        this(value.length());
        for (int i = 0; i < value.length(); i++) {
            array[i] = (byte) value.charAt(i);
        }
        length = value.length();
    }

    public AsciiString(final int capacity) {
        array = new byte[capacity];
        length = 0;
        address = ARRAY_BYTE_BASE_OFFSET;
    }

    public byte[] getArray() {
        return array;
    }

    public long getAddress() {
        return address;
    }

    public int getLength() {
        return length;
    }

    public AsciiString append(final int value) {
        if (value == Integer.MIN_VALUE) {
            ensureCapacity(length + MIN_INTEGER_VALUE.length);
            System.arraycopy(MIN_INTEGER_VALUE, 0, array, length, MIN_INTEGER_VALUE.length);
            length += MIN_INTEGER_VALUE.length;
            return this;
        }

        int quotient = value;
        final int digitCount = digitCount(value);
        ensureCapacity(length + digitCount);

        int index = length + digitCount;

        if (value < 0) {
            quotient = -quotient;
            array[length] = (byte) '-';
        }

        do {
            final int remainder = quotient % 10;
            quotient = quotient / 10;
            array[--index] = (byte) ('0' + remainder);
        }
        while (quotient > 0);

        length += digitCount;
        return this;
    }

    public AsciiString append(final long value) {
        if (value == Integer.MIN_VALUE) {
            ensureCapacity(length + MIN_INTEGER_VALUE.length);
            System.arraycopy(MIN_INTEGER_VALUE, 0, array, length, MIN_INTEGER_VALUE.length);
            length += MIN_INTEGER_VALUE.length;
            return this;
        }

        long quotient = value;
        final int digitCount = digitCount(value);
        ensureCapacity(length + digitCount);

        int index = length + digitCount;

        if (value < 0) {
            quotient = -quotient;
            array[length] = (byte) '-';
        }

        do {
            final long remainder = quotient % 10;
            quotient = quotient / 10;
            array[--index] = (byte) ('0' + remainder);
        }
        while (quotient > 0);

        length += digitCount;
        return this;
    }

    public AsciiString append(final byte[] bytes) {
        return append(bytes, 0, bytes.length);
    }

    public AsciiString append(final byte[] bytes, final int offset, final int len) {
        ensureCapacity(length + len);
        System.arraycopy(bytes, offset, array, length, len);
        length += len;
        return this;
    }

    public AsciiString append(final CharSequence value) {
        final int len = value.length();
        ensureCapacity(length + len);
        for (int i = 0; i < len; i++) {
            array[length + i] = (byte) value.charAt(i);
        }
        length += len;
        return this;
    }

    private void ensureCapacity(final int requiredCapacity) {
        if (requiredCapacity > array.length) {
            final int newCapacity = Math.max(requiredCapacity, array.length * 2);
            final byte[] newArray = new byte[newCapacity];
            System.arraycopy(array, 0, newArray, 0, length);
            array = newArray;
        }
    }

    private static int digitCount(int value) {
        if (value == 0) {
            return 1;
        }

        int count = value < 0 ? 1 : 0;
        value = value < 0 ? -value : value;

        while (value > 0) {
            value /= 10;
            count++;
        }
        return count;
    }

    private static int digitCount(long value) {
        if (value == 0) {
            return 1;
        }

        int count = value < 0 ? 1 : 0;
        value = value < 0 ? -value : value;

        while (value > 0) {
            value /= 10;
            count++;
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (!(o instanceof AsciiString)) {
            return false;
        }
        AsciiString other = (AsciiString) o;
        if (other.length != length) {
            return false;
        }

        int i = 0;
        while (i + 8 <= length) {
            long fstLong = UNSAFE.getLong(array, address + i);
            long sndLong = UNSAFE.getLong(other.array, other.address + i);

            if (fstLong != sndLong) {
                return false;
            }
            i += 8;
        }

        if (i + 4 <= length) {
            int fstInt = UNSAFE.getInt(array, address + i);
            int sndInt = UNSAFE.getInt(other.array, other.address + i);

            if (fstInt != sndInt) {
                return false;
            }
            i += 4;
        }

        if (i + 2 <= length) {
            int fstShort = UNSAFE.getShort(array, address + i);
            int sndShort = UNSAFE.getShort(other.array, other.address + i);

            if (fstShort != sndShort) {
                return false;
            }
            i += 2;
        }
        return i == length || UNSAFE.getByte(array, address + i) == UNSAFE.getByte(other.array, other.address + i);
    }

    @Override
    public String toString() {
        return new String(array, 0, length);
    }

    @Override
    public int hashCode() {
        return NativeHashCodeComputer.INSTANCE.hashCode(this);
    }
}