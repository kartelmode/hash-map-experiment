package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static maps.HashMapBenchmark.selectAsciiHashCodeComputer;

/** not really a JMH test :-) (extracted from main JMH test) */
public class HashMapCollisionsBenchmark {
    private static final long BASE_KEY_ID = 1_000_000_000_000L;

    public static void main(String[] args) {
        final String[] keyNamingStrategies = {
                "number",
                "mm",
                "uuid"
        };

        final String[] hashStrategies = {
                "varHandle",
                "xxHash",
                "default",
                "metroHash",
                "unrolledDefault",
                "vectorizedDefaultHash",
                "nativeHash",
                "faster",
                "vhFaster"
        };

        final int rows = hashStrategies.length;
        final int cols = keyNamingStrategies.length;

        // Collect results: collisions[row(hash)][col(keyNaming)]
        final long[][] collisions = new long[rows][cols];

        // Simulation constants
        final int maxActiveKeys   = 1 << 20;  // 1,048,576
        final int maxInactiveKeys = 4096;
        final int iterations      = 1 << 25;

        for (int r = 0; r < rows; r++) {
            final String hashStrategy = hashStrategies[r];
            for (int c = 0; c < cols; c++) {
                final String keyNaming = keyNamingStrategies[c];
                System.out.println("Testing \"" + hashStrategy + "\" hashing with \"" + keyNaming + "\" key kind...");
                collisions[r][c] = runOnce(hashStrategy, keyNaming, maxActiveKeys, maxInactiveKeys, iterations);
            }
        }

        // -------- Formatting & printing --------
        // First column width (hash names)
        int firstColWidth = Math.max("Hash".length(),
                Arrays.stream(hashStrategies).mapToInt(String::length).max().orElse(4));

        // Widths for each data column: max of header (keyNaming) and max collision digits in that column
        int[] colWidths = new int[cols];
        for (int c = 0; c < cols; c++) {
            int maxDigits = keyNamingStrategies[c].length();
            for (int r = 0; r < rows; r++) {
                int len = Long.toString(collisions[r][c]).length();
                if (len > maxDigits) maxDigits = len;
            }
            colWidths[c] = maxDigits;
        }

        // Header

        StringBuilder sb = new StringBuilder();
        sb.append("\nRESULTS: number of collisions\ndepending on hash and key naming pattern:\n");
        sb.append(padRight("Hash", firstColWidth));
        for (int c = 0; c < cols; c++) {
            sb.append("  ").append(padLeft(keyNamingStrategies[c], colWidths[c]));
        }
        sb.append('\n');

        // Separator
        sb.append(line(firstColWidth));
        for (int c = 0; c < cols; c++) {
            sb.append("  ").append(line(colWidths[c]));
        }
        sb.append('\n');

        // Rows
        for (int r = 0; r < rows; r++) {
            sb.append(padRight(hashStrategies[r], firstColWidth));
            for (int c = 0; c < cols; c++) {
                sb.append("  ").append(padLeft(Long.toString(collisions[r][c]), colWidths[c]));
            }
            sb.append('\n');
        }

        System.out.print(sb.toString());
    }

    private static long runOnce(String hashStrategy,
                                String keyNaming,
                                int maxActiveKeys,
                                int maxInactiveKeys,
                                int iterations) {
        final KeyNamingStrategy keyNamingStrategy = KeyNamingStrategy.select(keyNaming);

        final Deque<DataPayload> activeSet = new ArrayDeque<>(maxActiveKeys);
        final int cacheCapacity = 2 * maxActiveKeys;
        final HashCodeComputer hash = selectAsciiHashCodeComputer(hashStrategy);
        final Cache cache = new ChainingHashMap(cacheCapacity, maxInactiveKeys, hash);

        for (int i = 0; i < iterations; i++) {
            if (i >= maxActiveKeys) {
                DataPayload oldestActive = activeSet.removeFirst(); // head
                cache.deactivate(oldestActive);
            }

            DataPayload newest = new DataPayload(new AsciiString(keyNamingStrategy.formatKey(i + BASE_KEY_ID)));
            cache.putIfEmpty(newest);
            activeSet.addLast(newest); // tail
        }
        return cache.collisionCount();
    }

    // ----- tiny helpers -----
    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        char[] pad = new char[width - s.length()];
        Arrays.fill(pad, ' ');
        return s + new String(pad);
    }

    private static String padLeft(String s, int width) {
        if (s.length() >= width) return s;
        char[] pad = new char[width - s.length()];
        Arrays.fill(pad, ' ');
        return new String(pad) + s;
    }

    private static String line(int count) {
        char[] arr = new char[count];
        Arrays.fill(arr, '-');
        return new String(arr);
    }
}
