package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;

import java.util.Arrays;
import java.util.function.Function;

import static maps.HashMapBenchmark.selectAsciiHashCodeComputer;

/** not really a JMH test :-) (extracted from main JMH test) */
public class HashMapCollisionsBenchmark {
    private static final long BASE_KEY_ID = 1_000_000_000_000L;

    private static final String[] KEY_STRATEGIES = {
            "number",
            "mm",
            "uuid"
    };

    private static final String[] HASH_STRATEGIES = {
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

    public static void main(String[] args) {
        // Collect results: collisions[row(hash)][col(keyNaming)]
        final BasicMetrics[][] metricsMatrix = new BasicMetrics[HASH_STRATEGIES.length][KEY_STRATEGIES.length];

        // Simulation constants
        final int maxActiveKeys = 1 << 20;  // 1,048,576

        for (int r = 0; r < HASH_STRATEGIES.length; r++) {
            final String hashStrategy = HASH_STRATEGIES[r];
            for (int c = 0; c < KEY_STRATEGIES.length; c++) {
                final String keyNaming = KEY_STRATEGIES[c];
                System.out.println("Testing \"" + hashStrategy + "\" hashing with \"" + keyNaming + "\" key kind...");
                metricsMatrix[r][c] = runOnce(hashStrategy, keyNaming, maxActiveKeys);
            }
        }

        printMetric(metricsMatrix, "Empty Bucket Ratio (smaller is better)", m->String.format("%.6f", m.emptyRate));
        printMetric(metricsMatrix, "Index of dispersion (≈1 =good, »1 =clustering, «1 = suspicious)", m->String.format("%.6f", m.iod));
        printMetric(metricsMatrix, "Percentiles {P50,P90,P99,P999}", m->String.format("{%d, %d, %d, %d}", m.p50, m.p90, m.p99, m.p999));
    }

    private static void printMetric(BasicMetrics[][] metricsMatrix, String metricName, Function<BasicMetrics,String> metricExtractor) {
        // -------- Formatting & printing --------
        // First column width (hash names)
        int firstColWidth = Math.max("Hash".length(),
                Arrays.stream(HASH_STRATEGIES).mapToInt(String::length).max().orElse(4));

        // Widths for each data column: max of header (keyNaming) and max collision digits in that column
        int[] colWidths = new int[KEY_STRATEGIES.length];
        for (int c = 0; c < KEY_STRATEGIES.length; c++) {
            int maxDigits = KEY_STRATEGIES[c].length();
            for (int r = 0; r < HASH_STRATEGIES.length; r++) {
                BasicMetrics metrics = metricsMatrix[r][c];
                String metricText = metricExtractor.apply(metrics);
                int len = metricText.length();
                if (len > maxDigits) maxDigits = len;
            }
            colWidths[c] = maxDigits;
        }

        // Header
        StringBuilder sb = new StringBuilder();
        sb.append("\nRESULTS: ").append(metricName).append("\ndepending on hash and key naming pattern:\n");
        sb.append(padRight("Hash", firstColWidth));
        for (int c = 0; c < KEY_STRATEGIES.length; c++) {
            sb.append("  ").append(padLeft(KEY_STRATEGIES[c], colWidths[c]));
        }
        sb.append('\n');

        // Separator
        sb.append(line(firstColWidth));
        for (int c = 0; c < KEY_STRATEGIES.length; c++) {
            sb.append("  ").append(line(colWidths[c]));
        }
        sb.append('\n');

        // Rows
        for (int r = 0; r < HASH_STRATEGIES.length; r++) {
            sb.append(padRight(HASH_STRATEGIES[r], firstColWidth));
            for (int c = 0; c < KEY_STRATEGIES.length; c++) {
                BasicMetrics metrics = metricsMatrix[r][c];
                String metricText = metricExtractor.apply(metrics);
                sb.append("  ").append(padLeft(metricText, colWidths[c]));
            }
            sb.append('\n');
        }

        System.out.print(sb);
    }

    public record BasicMetrics(float lambda, float iod, float emptyRate, int p50, int p90, int p99, int p999) {}

    private static BasicMetrics runOnce(String hashStrategy, String keyNaming, int maxActiveKeys) {
        final KeyNamingStrategy keyNamingStrategy = KeyNamingStrategy.select(keyNaming);

        final int m = 2 * maxActiveKeys; // total number of hash buckets (0.5 load factor)
        final int [] counts = new int[m];
        final HashCodeComputer hashComputer = selectAsciiHashCodeComputer(hashStrategy);

        final int n = 10 * m; // total number of inserted keys
        for (int i = 0; i < n; i++) {
            AsciiString key = keyNamingStrategy.formatKey(i + BASE_KEY_ID);
            counts[hashComputer.modPowerOfTwoHashCode(key, m)]++;
        }

        return analyzeResults(n, m, counts);
    }

    private static BasicMetrics analyzeResults(int n, int m, int[] counts) {
        int lambda = n / m;
        float variance = 0;
        int emptyBucketCount = 0;
        for (int v : counts) {
            if (v == 0)
                emptyBucketCount++;

            float delta = (v - lambda);
            variance += delta * delta;
        }
        variance /= m;

        float iod = variance / lambda;
        float emptyRate = ((float)emptyBucketCount) / m;

        int[] sorted = counts.clone();
        Arrays.sort(sorted);
        int p50 = sorted[(int)(0.50 * (sorted.length - 1))];
        int p90 = sorted[(int)(0.90 * (sorted.length - 1))];
        int p99 = sorted[(int)(0.99 * (sorted.length - 1))];
        int p999 = sorted[(int)(0.999 * (sorted.length - 1))];

        return new BasicMetrics(lambda, iod, emptyRate, p50, p90, p99, p999);
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
