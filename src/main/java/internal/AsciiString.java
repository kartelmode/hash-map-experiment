package internal;

import static internal.UnsafeAccess.UNSAFE;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

public class AsciiString {
    private byte[] array;
    private int length = 0;
    protected long address;

    public AsciiString() {
        this.array = new byte[1];
        this.length = 0;
    }

    public AsciiString(final CharSequence value) {
        setString(value);
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

    public void setString(final CharSequence value) {
        array = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            array[i] = (byte) value.charAt(i);
        }
        length = value.length();
        address = ARRAY_BYTE_BASE_OFFSET;
    }

    public void copyFrom(final AsciiString value) {
        if (array.length < value.length) {
            int newLength = Math.max(array.length << 1, value.length);
            array = new byte[newLength];
        }
        System.arraycopy(value.array, 0, array, 0, value.length);
        length = value.length;
        address = ARRAY_BYTE_BASE_OFFSET;
    }

    public boolean equals(AsciiString other) {
        if (other == null) {
            return false;
        }
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char)array[i]);
        }
        return sb.toString();
    }
}
