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
import java.util.concurrent.TimeUnit;

@Fork(value=3, jvmArgs = { "-Xms16G", "-Xmx16G", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 15)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@Threads(1)
public class HashMapBenchmark {
    private static final long BASE_KEY_ID = 1_000_000_000_000L;
    private static final int KEY_UNIVERSE_SIZE = 10_000_000;

    //    @Param({"128", "4096", "8192"})
    @Param({"4096"})
    protected int maxInactiveKeys = 4096;

    //    @Param({"4096", "16384", "1048576", "2097152"}) //NB: higher counts may not have time to fully fill during short test run time
    @Param({"1048576"})
    protected int maxActiveKeys = 1048576; // must be power of 2

//    @Param({"xxHash", "default", "unrolledDefault", "nativeHash", "vectorizedDefaultHash"})
    protected String hashStrategy = "xxHash";

//    @Param({"75", "50", "40", "25", "10"})
    protected int loadFactor = 50;

    private KeyNamingStrategy keyNamingStrategy;

    private long nextKeyId;

    private ChainingHashMap map;

    private DataPayload[] universe;

    @Setup
    public void init() {
        map = new ChainingHashMap(2*maxActiveKeys, maxInactiveKeys, selectAsciiHashCodeComputer(hashStrategy));
        keyNamingStrategy = new KeyNamingStrategy();

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
            int nextActive = (int) (nextKeyId % KEY_UNIVERSE_SIZE);
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
    }

    private void removeOldest() {
        final int oldestActive = (int) ((nextKeyId - maxActiveKeys) % KEY_UNIVERSE_SIZE);
        map.deactivate(universe[oldestActive]);
    }

    private void addNewest() {
        final int nextActive = (int) (nextKeyId % KEY_UNIVERSE_SIZE);
        boolean isNew = map.putIfEmpty(universe[ nextActive]);
        if (!isNew)
            throw new IllegalStateException("Duplicate");
    }

    @Benchmark
    @OperationsPerInvocation(2)
    public void benchmark() {
        removeOldest();
        addNewest();
        nextKeyId++;

        //TODO: get()
    }

    private static HashCodeComputer selectAsciiHashCodeComputer(String hashingStrategyName) {
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
        runCountingCollisions();

        runJMH();
    }

    private static void runJMH() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(HashMapBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }

    private static void runCountingCollisions() {

        ArrayList<String> hashes = new ArrayList<>();
        hashes.add("varHandle");
        hashes.add("xxHash");
        hashes.add("default");
        hashes.add("metroHash");
        hashes.add("unrolledDefault");
        hashes.add("vectorizedDefaultHash");
        hashes.add("nativeHash");
        hashes.add("faster");
        hashes.add("vhFaster");


        int nameWidth = hashes.stream()
                .mapToInt(String::length)
                .max()
                .orElse(0);

        String resultsFormat = "Collisions count for %-" + nameWidth + "s : %12d%n";

        for (String hash : hashes) {
            HashMapBenchmark bench = new HashMapBenchmark();
            bench.hashStrategy = hash;
            bench.init();

            for (int i = 0; i < (1 << 25); i++) {
                bench.benchmark();
            }
            System.out.printf(resultsFormat, hash, bench.map.getCollisions());
        }
    }

    public static final class KeyNamingStrategy {

        public AsciiString formatKey(long sequence) {
            return new AsciiString(Long.toString(sequence));
        }
    }
}
