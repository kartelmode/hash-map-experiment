package maps;

import hashing.*;
import internal.AsciiString;
import internal.DataPayload;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Fork(value=3, jvmArgs = { "-Xms4G", "-Xmx4G", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 15)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime}) // SampleTime: per-invocation latency percentiles (p50..p99.99, max)
@State(Scope.Thread)
@Threads(1)
public class HashMapBenchmark {
    private static final long BASE_KEY_ID = 1_000_000_000_000L;

    private static final int KEY_UNIVERSE_SIZE = 2097152;
    {
        assert Integer.bitCount(KEY_UNIVERSE_SIZE) == 1;
    }
    private static final int KEY_UNIVERSE_MASK = (KEY_UNIVERSE_SIZE - 1);

    //    @Param({"128", "4096", "8192"})
    @Param({"4096"})
    private int maxInactiveKeys = 4096;

    //NB: closely related to KEY_UNIVERSE_SIZE: > maxInactiveKeys + maxActiveKeys
    private int maxActiveKeys = 1048576; // must be power of 2

    @Param({"xxHash", "default", "unrolledDefault", "nativeHash", "vectorizedDefaultHash"})
    private String hashStrategy = "xxHash";

    @Param({"chaining", "linearprobe", "storedHashLinearprobe", "robinhood", "nativeLinearprobe", "rawLinearprobe", "javaUtil"})
    private String mapClass = "javaUtil";

    @Param({"number", "fixed_prefix", "uuid"})
    private String keyNaming = "number";

    /** Order in which active keys are deactivated: oldest first, or a uniformly random active key. */
    @Param({"fifo", "random"})
    private String deactivationOrder = "fifo";

    @Param({"0.5"})
    private double loadFactor = 0.5;

    private static final float NO_RESIZE_LOAD_FACTOR = 0.75f;

    /** Fixed seed so that every fork/configuration sees the same allocation order. */
    private static final long ALLOCATION_SHUFFLE_SEED = 0x5EED_1234_ABCDL;

    /** Capacity of the lookup flyweight; larger than any generated key, so lookups never reallocate. */
    private static final int LOOKUP_KEY_CAPACITY = 128;

    private static final long RNG_SEED = 0x9E37_79B9_7F4A_7C15L;

    private KeyNamingStrategy keyNamingStrategy;
    private boolean randomDeactivation;
    private int[] activeKeys;     // universe indices of active keys
    private boolean[] inMap;      // universe index -> is in the map (active or inactive)
    private int[] inactiveKeys;   // ring mirroring the map's FIFO inactive queue
    private int inactiveCount;
    private int lastExpunged;     // universe index of the key evicted by the last deactivate()
    private int fifoPosition;
    private long rngState;
    private Cache map;
    private long nextKeyId;

    private DataPayload[] universe;

    /**
     * Simulated "wire": the bytes of every key packed contiguously in sequence order, like decoded inbound messages.
     * Lookups decode from here into {@link #lookupKey}, so they never touch (or share identity with) the stored key.
     */
    private byte[] wireBytes;
    private int[] wireOffsets; // key i occupies wireBytes[wireOffsets[i] .. wireOffsets[i+1])

    /** Reusable lookup key, like a flyweight decoded from an inbound message. Never stored in the map. */
    private final AsciiString lookupKey = new AsciiString(LOOKUP_KEY_CAPACITY);

    private int tableCapacity;

    @Setup
    public void init() {
        tableCapacity = tableCapacityFor(maxActiveKeys + maxInactiveKeys, loadFactor);
        map = selectCache(mapClass);
        keyNamingStrategy = KeyNamingStrategy.select(keyNaming);
        randomDeactivation = switch (deactivationOrder) {
            case "fifo" -> false;
            case "random" -> true;
            default -> throw new IllegalArgumentException(deactivationOrder);
        };

        initUniverse();
        initWire();
        prepopulateMap();
        verifyNoResize();
        verifyLookupKeysAreDistinct();

        // Compact once more so that measurement starts from a settled old generation.
        // A full GC slides objects without reordering them, so the shuffled layout survives.
        System.gc();
    }

    private void initUniverse() {
        final int[] permutation = shuffledPermutation(KEY_UNIVERSE_SIZE, ALLOCATION_SHUFFLE_SEED);

        final DataPayload[] allocationOrder = new DataPayload[KEY_UNIVERSE_SIZE];
        for (int j = 0; j < KEY_UNIVERSE_SIZE; j++) {
            allocationOrder[j] = new DataPayload(keyNamingStrategy.formatKey(permutation[j] + BASE_KEY_ID));
        }

        System.gc(); // tenure the payloads in allocation (= shuffled) order

        universe = new DataPayload[KEY_UNIVERSE_SIZE];
        for (int j = 0; j < KEY_UNIVERSE_SIZE; j++) {
            universe[permutation[j]] = allocationOrder[j];
        }
    }

    private static int[] shuffledPermutation(int size, long seed) {
        final int[] permutation = new int[size];
        for (int i = 0; i < size; i++)
            permutation[i] = i;

        final Random rnd = new Random(seed);
        for (int i = size - 1; i > 0; i--) { // Fisher-Yates
            final int j = rnd.nextInt(i + 1);
            final int tmp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = tmp;
        }
        return permutation;
    }

    /** Copies key bytes into a separate contiguous buffer, in key sequence order. */
    private void initWire() {
        wireOffsets = new int[KEY_UNIVERSE_SIZE + 1];
        long total = 0;
        for (int i = 0; i < KEY_UNIVERSE_SIZE; i++) {
            final int length = universe[i].getKey().getLength();
            if (length > LOOKUP_KEY_CAPACITY)
                throw new IllegalStateException("Key is longer than the lookup flyweight: " + length);
            total += length;
        }
        if (total > Integer.MAX_VALUE - 16)
            throw new IllegalStateException("Wire buffer too large: " + total);

        wireBytes = new byte[(int) total];
        int offset = 0;
        for (int i = 0; i < KEY_UNIVERSE_SIZE; i++) {
            final AsciiString key = universe[i].getKey();
            System.arraycopy(key.getArray(), 0, wireBytes, offset, key.getLength());
            wireOffsets[i] = offset;
            offset += key.getLength();
        }
        wireOffsets[KEY_UNIVERSE_SIZE] = offset;
    }

    /** Decodes the key with the given universe index from the wire into the lookup flyweight. */
    private AsciiString decodeLookupKey(int universeIndex) {
        final int offset = wireOffsets[universeIndex];
        return lookupKey.set(wireBytes, offset, wireOffsets[universeIndex + 1] - offset);
    }

    private void verifyLookupKeysAreDistinct() {
        for (int i = 0; i < KEY_UNIVERSE_SIZE; i++) {
            final AsciiString stored = universe[i].getKey();
            final AsciiString lookup = decodeLookupKey(i);
            if (lookup == stored || lookup.getArray() == stored.getArray() || !lookup.equals(stored)
                    || lookup.hashCode() != stored.hashCode())
                throw new IllegalStateException("Lookup key must be an equal but distinct copy of " + stored);
        }
    }

    static int tableCapacityFor(int size, double targetLoadFactor) {
        if (targetLoadFactor <= 0 || targetLoadFactor >= 1)
            throw new IllegalArgumentException("loadFactor must be in (0, 1): " + targetLoadFactor);
        final int log2 = (int) Math.round(Math.log(size / targetLoadFactor) / Math.log(2));
        final int capacity = 1 << log2;
        final double actual = (double) size / capacity;
        if (Math.abs(actual / targetLoadFactor - 1) > 0.1)
            throw new IllegalArgumentException("loadFactor " + targetLoadFactor + " can't be reached with a power-of-two table"
                    + " for " + size + " keys (closest: " + actual + " with " + capacity + " slots); change maxActiveKeys");
        return capacity;
    }

    /** The open-addressing and chaining tables must not have grown, otherwise the load factor isn't the configured one. */
    private void verifyNoResize() {
        final int expected = mapClass.equals("javaUtil") ? 0 : tableCapacity; // JavaHashMap doesn't expose its table
        if (map.capacity() != expected)
            throw new IllegalStateException(mapClass + " resized: capacity " + map.capacity() + " != " + tableCapacity);
        System.out.printf("%n# %s: %d keys in %d slots, load factor %.3f%n",
                mapClass, map.size(), tableCapacity, (double) map.size() / tableCapacity);
    }

    /**
     * Keys are always inserted in sequence order (like ClOrdIDs from a counter). They are deactivated either
     * oldest-first ({@code fifo}, the original benchmark) or a uniformly random active key at a time ({@code random},
     * like orders that complete out of order). {@link #activeKeys} holds the universe indices of the active keys;
     * the new key takes the slot of the deactivated one.
     */
    private void prepopulateMap() {
        activeKeys = new int[maxActiveKeys];
        inMap = new boolean[KEY_UNIVERSE_SIZE];
        inactiveKeys = new int[maxInactiveKeys];
        rngState = RNG_SEED;
        lastExpunged = -1;

        for (int i = 0; i < maxActiveKeys; i++)
            activeKeys[i] = insertNewest();

        // Fill the inactive queue, plus one step so that lastExpunged refers to a real evicted key
        for (int i = 0; i <= maxInactiveKeys; i++) {
            final int position = nextVictimPosition();
            deactivate(activeKeys[position]);
            activeKeys[position] = insertNewest();
        }
        if (map.size() != maxActiveKeys + maxInactiveKeys)
            throw new IllegalStateException("Unexpected map size " + map.size());
        if (lastExpunged < 0)
            throw new IllegalStateException("Nothing expunged during warm-up");
    }

    /** xorshift64: cheap, allocation-free. Advanced in both modes, so both pay the same cost. */
    private int nextVictimPosition() {
        long x = rngState;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        rngState = x;
        if (randomDeactivation)
            return (int) (x >>> 1) & (maxActiveKeys - 1);
        final int position = fifoPosition;
        fifoPosition = (position + 1) & (maxActiveKeys - 1);
        return position;
    }

    private void findActive(int universeIndex) {
        boolean exist = map.get(decodeLookupKey(universeIndex)) != null;
        if (!exist)
            throw new IllegalStateException("Unknown: " + lookupKey);
    }

    private void findExpunged() {
        AsciiString key = decodeLookupKey(lastExpunged);
        boolean exist = map.get(key) != null;
        if (exist)
            throw new IllegalStateException("Key persisted: " + key);
    }

    /** Mirrors the map's FIFO inactive queue to know which key it evicts. */
    private void deactivate(int universeIndex) {
        map.deactivate(universe[universeIndex]);
        final int slot = inactiveCount & (maxInactiveKeys - 1);
        if (inactiveCount >= maxInactiveKeys) {
            lastExpunged = inactiveKeys[slot];
            inMap[lastExpunged] = false;
        }
        inactiveKeys[slot] = universeIndex;
        inactiveCount++;
    }

    /**
     * Inserts the next key in sequence. With random deactivation a key may outlive a full turn of the universe;
     * such indices are skipped, so new keys stay sequential, with rare gaps. The key just expunged is skipped too:
     * the next invocation looks it up as a guaranteed miss.
     */
    private int insertNewest() {
        int universeIndex = (int) (nextKeyId & KEY_UNIVERSE_MASK);
        while (inMap[universeIndex] || universeIndex == lastExpunged) {
            nextKeyId++;
            universeIndex = (int) (nextKeyId & KEY_UNIVERSE_MASK);
        }
        nextKeyId++;
        if (!map.putIfEmpty(universe[universeIndex]))
            throw new IllegalStateException("Duplicate");
        inMap[universeIndex] = true;
        return universeIndex;
    }

    @Benchmark
    @OperationsPerInvocation(4)
    public void benchmark() {
        final int position = nextVictimPosition();
        final int victim = activeKeys[position];
        findActive(victim);                     // GET (hit: e.g. an ack for a live order)
        findExpunged();                         // GET (miss)
        deactivate(victim);                     // REMOVE
        activeKeys[position] = insertNewest();  // PUT
    }

    /**
     * Evicts the TLB (and L1/L2) before every operation by reading one cache line in each of {@link #pollutionPages}
     * distinct OS pages (4 KB on x86/Graviton Linux, 16 KB on Apple Silicon macOS), more pages than any current
     * second-level TLB holds (about 1.5-3K entries). The map's next
     * operation then needs page walks for the pages it touches, like after Ember's other per-message work.
     * <p>
     * The pollution runs in a {@code Level.Invocation} setup, so it is excluded from the measured time. JMH then reads
     * the clock around every invocation, which adds roughly 20-30 ns to each operation on Linux (the same for all maps).
     * This produces TLB misses (page walks), not OS page faults: the heap is pre-touched, as in production.
     * The stride is the OS base page size; with transparent huge pages enabled for the heap, the buffer would span
     * only a few huge pages and the pollution would not evict the TLB.
     */
    @State(Scope.Thread)
    public static class TlbPollution {
        private static final int PAGE_SIZE = internal.UnsafeAccess.UNSAFE.pageSize();
        private static final int LONGS_PER_PAGE = PAGE_SIZE / Long.BYTES;
        private static final int LONGS_PER_LINE = 64 / Long.BYTES;

        @Param({"4096"})
        public int pollutionPages;

        private long[] buffer;
        private int lineOffset;
        public long sink; // keeps the reads alive

        @Setup(Level.Trial)
        public void allocate() {
            if (Integer.bitCount(PAGE_SIZE) != 1)
                throw new IllegalStateException("Unexpected page size " + PAGE_SIZE);
            buffer = new long[pollutionPages * LONGS_PER_PAGE];
            for (int i = 0; i < buffer.length; i += LONGS_PER_LINE)
                buffer[i] = i; // touch every page and line
        }

        @Setup(Level.Invocation)
        public void pollute() {
            final long[] b = buffer;
            final int pages = pollutionPages;
            long sum = 0;
            for (int page = 0; page < pages; page++)
                sum += b[page * LONGS_PER_PAGE + lineOffset];
            lineOffset = (lineOffset + LONGS_PER_LINE) & (LONGS_PER_PAGE - 1); // a different line next time
            sink += sum;
        }
    }

    private int pollutedPhase;
    private int pendingPosition;
    private int pendingVictim;

    /**
     * Same workload as {@link #benchmark()}, but one map operation per invocation, so that the TLB is polluted
     * before each of them. Reports the average time of a single operation (hit, miss, remove, put in rotation).
     */
    @Benchmark
    public void benchmarkPolluted(TlbPollution pollution) {
        switch (pollutedPhase) {
            case 0 -> {
                pendingPosition = nextVictimPosition();
                pendingVictim = activeKeys[pendingPosition];
                findActive(pendingVictim);                      // GET (hit)
            }
            case 1 -> findExpunged();                           // GET (miss)
            case 2 -> deactivate(pendingVictim);                // REMOVE
            default -> activeKeys[pendingPosition] = insertNewest(); // PUT
        }
        pollutedPhase = (pollutedPhase + 1) & 3;
    }



    /** Every map gets exactly {@link #tableCapacity} slots (buckets), so all run at the same load factor. */
    private Cache selectCache(String cacheClass) {
        final HashCodeComputer hash = selectAsciiHashCodeComputer(hashStrategy);
        final int noResizePercent = Math.round(NO_RESIZE_LOAD_FACTOR * 100);

        return switch (cacheClass) {
            // chaining: tableCapacity buckets and as many nodes; grows only when all nodes are used (load 1.0)
            case "chaining" -> new ChainingHashMap(tableCapacity, maxInactiveKeys, hash);
            case "linearprobe" -> LinearProbingHashMap.withTableCapacity(tableCapacity, maxInactiveKeys, hash, NO_RESIZE_LOAD_FACTOR);
            case "storedHashLinearprobe" -> StoredHashLinearProbingHashMap.withTableCapacity(tableCapacity, maxInactiveKeys, hash, NO_RESIZE_LOAD_FACTOR);
            case "robinhood" -> RobinHoodHashMap.withTableCapacity(tableCapacity, maxInactiveKeys, hash, NO_RESIZE_LOAD_FACTOR);
            case "nativeLinearprobe" -> NativeLinearProbingHashMap.withTableCapacity(tableCapacity, maxInactiveKeys, hash, noResizePercent);
            case "rawLinearprobe" -> RawLinearProbingHashMap.withTableCapacity(tableCapacity, maxInactiveKeys, hash, noResizePercent);
            // HashMap: table = tableCapacity buckets (a power of two), resized at its default load factor 0.75
            case "javaUtil" -> new JavaHashMap(tableCapacity, maxInactiveKeys);
            default -> throw new IllegalArgumentException(cacheClass);
        };
    }

    static HashCodeComputer selectAsciiHashCodeComputer(String hashingStrategyName) {
        return switch (hashingStrategyName) {
            case "xxHash" -> XxHashCodeComputer.INSTANCE;
            case "default" -> DefaultHashCodeComputer.INSTANCE;
            case "metroHash" -> MetroHashCodeComputer.INSTANCE;
            case "faster" -> FasterHashCodeComputer.INSTANCE;
            case "vhFaster" -> vhFasterHashCodeComputer.INSTANCE;
            case "varHandle" -> vhHashCodeComputer.INSTANCE;
            case "unrolledDefault" -> UnrolledDefaultHashCodeComputer.INSTANCE;
            case "nativeHash" -> NativeHashCodeComputer.INSTANCE;
            case "vectorizedDefaultHash" -> VectorizedDefaultHashCodeComputer.INSTANCE;
            default -> throw new IllegalArgumentException(hashingStrategyName);
        };
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(HashMapBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }

}