package maps;

import hashing.*;
import internal.AsciiString;
import internal.DataPayload;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Fork(value=3, jvmArgs = { "-Xms4G", "-Xmx4G", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 15)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
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

    @Param({"chaining", "linearprobe", "robinhood"})
    private String mapClass = "chaining";

    @Param({"number", "mm", "uuid"})
    private String keyNaming = "number";

    private KeyNamingStrategy keyNamingStrategy;
    private Cache map;
    private long nextKeyId;
    private DataPayload[] universe;

    @Setup
    public void init() {
        map = selectCache(mapClass);
        keyNamingStrategy = KeyNamingStrategy.select(keyNaming);

        initUniverse();
        prepopulateMap();
    }

    private void initUniverse() {
        universe = new DataPayload[KEY_UNIVERSE_SIZE];

        for (int i = 0; i < KEY_UNIVERSE_SIZE; i++) {
            universe[i] = new DataPayload(keyNamingStrategy.formatKey(i + BASE_KEY_ID));
        }
    }

    private void prepopulateMap() {
        for (int i = 0; i < maxActiveKeys; i++) {
            int nextActive = (int) (nextKeyId & KEY_UNIVERSE_MASK);
            boolean isNew = map.putIfEmpty(universe[ nextActive]);
            if (!isNew)
                throw new IllegalStateException("Duplicate");
            nextKeyId++;
        }
        for (int i=0; i < maxInactiveKeys; i++) {
            removeOldest();
            addNewest();
            nextKeyId++;
        }
        if (map.size() != maxActiveKeys + maxInactiveKeys)
            throw new IllegalStateException("Unexpected map size " + map.size());
    }

    private void findOldest() {
        final int oldestActive = (int) ((nextKeyId - maxActiveKeys) & KEY_UNIVERSE_MASK);
        boolean exist = map.get(universe[oldestActive].getKey()) != null;
        if (!exist)
            throw new IllegalStateException("Unknown");
    }

    private void findExpunged() {
        final int oldestInactive = (int) ((nextKeyId - maxActiveKeys - maxInactiveKeys - 1) & KEY_UNIVERSE_MASK);
        AsciiString key = universe[oldestInactive].getKey();
        boolean exist = map.get(key) != null;
        if (exist)
            throw new IllegalStateException("Key persisted: " + key);
    }

    private void removeOldest() {
        final int oldestActive = (int) ((nextKeyId - maxActiveKeys) & KEY_UNIVERSE_MASK);
        map.deactivate(universe[oldestActive]);
    }

    private void addNewest() {
        final int nextActive = (int) (nextKeyId & KEY_UNIVERSE_MASK);
        boolean isNew = map.putIfEmpty(universe[ nextActive]);
        if (!isNew)
            throw new IllegalStateException("Duplicate");
    }

    @Benchmark
    @OperationsPerInvocation(4)
    public void benchmark() {
        findOldest();   // GET
        findExpunged(); // GET
        removeOldest(); // REMOVE
        addNewest();    // PUT
        nextKeyId++;
    }



    private Cache selectCache(String cacheClass) {
        final int cacheCapacity = 2*maxActiveKeys;
        final HashCodeComputer hash = selectAsciiHashCodeComputer(hashStrategy);

        return switch (cacheClass) {
            case "chaining" -> new ChainingHashMap(cacheCapacity, maxInactiveKeys, hash);
            case "linearprobe" -> new LinearProbingHashMap(cacheCapacity, maxInactiveKeys, hash);
            case "robinhood" -> new RobinHoodHashMap(cacheCapacity, maxInactiveKeys, hash);
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

