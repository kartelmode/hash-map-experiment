package maps;


import internal.AsciiString;

import java.util.Random;

interface KeyNamingStrategy {
    AsciiString formatKey(long sequence);

    static KeyNamingStrategy select (String keyNaming) {
        return switch (keyNaming) {
            case "number" -> new NumberKeyNamingStrategy();
            case "mm" -> new MMNamingStrategy();
            case "uuid" -> new UUIDNamingStrategy();
            default -> throw new IllegalArgumentException(keyNaming);
        };
    }
}

final class NumberKeyNamingStrategy implements KeyNamingStrategy {
    @Override
    public AsciiString formatKey(long sequence) {
        return new AsciiString(Long.toString(sequence));
    }

}

final class MMNamingStrategy implements KeyNamingStrategy {
    private static final String PREFIX = "SOURCE13:";
    private final StringBuilder sb = new StringBuilder().append(PREFIX);
    @Override
    public AsciiString formatKey(long sequence) {
        sb.setLength(PREFIX.length());
        sb.append(Long.toString(sequence, 32));
        return new AsciiString(sb);
    }
}

final class UUIDNamingStrategy implements KeyNamingStrategy {
    private static final char[] HEX_DIGITS_UPPER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private final Random rnd = new Random(152);
    private final StringBuilder sb = new StringBuilder();
    @Override
    public AsciiString formatKey(long sequence) {
        sb.setLength(0);
        long msb = rnd.nextLong();
        long lsb = rnd.nextLong();

        /* Clear version. */
        msb &= 0xFFFF_FFFF_FFFF_0FFFL;
        /* Set to version 4. */
        msb |= 0x0000_0000_0000_4000L;
        /* Clear variant. */
        lsb &= 0x3FFF_FFFF_FFFF_FFFFL;
        /* Set to IETF variant. */
        lsb |= 0x8000_0000_0000_0000L;

        for (int i = 15; i > 7; i -= 1)
            sb.append(HEX_DIGITS_UPPER[(int) (msb >> (i * 4)) & 0xF]);
        sb.append('-');
        for (int i = 7; i > 3; i -= 1)
            sb.append(HEX_DIGITS_UPPER[(int) (msb >> (i * 4)) & 0xF]);
        sb.append('-');
        for (int i = 3; i >= 0; i -= 1)
            sb.append(HEX_DIGITS_UPPER[(int) (msb >> (i * 4)) & 0xF]);
        sb.append('-');

        for (int i = 15; i > 11; i -= 1)
            sb.append(HEX_DIGITS_UPPER[(int) (lsb >> (i * 4)) & 0xF]);
        sb.append('-');
        for (int i = 11; i >= 0; i -= 1)
            sb.append(HEX_DIGITS_UPPER[(int) (lsb >> (i * 4)) & 0xF]);

        return new AsciiString(sb);
    }

}
