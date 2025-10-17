package maps;

import hashing.*;
import internal.AsciiString;
import internal.DataWrapper;
import internal.ObjectPool;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.RunnerException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

@Fork(value=3, jvmArgs = { "-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 15)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@Threads(1)
public class HashMapBenchmark {
    private static final long MAGIC_CONSTANT = 123456775432L;
    private static final long BASE_KEY_ID = 1000_000_000_000L;

    //    @Param({"128", "4096", "8192"})
    @Param({"4096"})
    protected int maxInactiveOrdersPerSource;

    //    @Param({"4096", "16384", "1048576", "2097152"}) //NB: higher counts may not have time to fully fill during short test run time
    @Param({"1048576"})
    protected int activeDataTotal; // must be power of 2

    @Param({"xxHash", "default", "unrolledDefault", "nativeHash"})
    protected String orderIdHashingStrategy;

//    @Param({"75", "50", "40", "25", "10"})
    protected int loadFactor;

    protected KeyNamingStrategy keyNamingStrategy;

    protected final AsciiString keyString = new AsciiString();
    protected final AsciiString deactivateKeyString = new AsciiString();
    protected long nextKeyId;

    protected DataWrapper data;
    protected DataWrapper deactivatingData;

    protected ChainingHashMap map;

    private ObjectPool<DataWrapper> pool;

    private final Deque<DataWrapper> activeData = new ArrayDeque<>();

    @Setup
    public void init() {
        pool = new ObjectPool<>(16 * 1024, DataWrapper::new);
        keyNamingStrategy = new KeyNamingStrategy(1);

        nextKeyId = BASE_KEY_ID;
        map = new ChainingHashMap(16 * 1024, maxInactiveOrdersPerSource, pool, selectAsciiHashCodeComputer(orderIdHashingStrategy));
        simulateActiveData();
    }

    @Setup(Level.Invocation)
    public void prepareMessages() {
        nextKeyId++;

        keyNamingStrategy.formatKey(nextKeyId, keyString);

        final long cancelKeyId = nextKeyId - activeDataTotal; // cancel the oldest active order
        keyNamingStrategy.formatKey(cancelKeyId, deactivateKeyString);

        data = pool.borrow();
        data.setActive(true);
        data.setAsciiString(keyString);
        data.setInCachePosition(-1);
        activeData.add(data);

        deactivatingData = activeData.getFirst();
        deactivatingData.setActive(false);
        activeData.removeFirst();
    }

    private void simulateActiveData() {
        for (int i = 0; i < activeDataTotal; i++) {
            nextKeyId++;
            keyNamingStrategy.formatKey(nextKeyId, keyString);
            data = pool.borrow();
            data.setActive(true);
            data.setAsciiString(keyString);
            data.setInCachePosition(-1);
            map.put(data);
            activeData.add(data);
        }
    }

    @Benchmark
    public void newDataPlusDeactivate() {
        map.deactivate(deactivatingData);
        map.put(data);
    }

    private static HashCodeComputer selectAsciiHashCodeComputer(String hashingStrategyName) {
        return switch (hashingStrategyName) {
            case "xxHash" -> XxHashCodeComputer.INSTANCE;
            case "default" -> DefaultHashCodeComputer.INSTANCE;
            case "metroHash" -> MetroHashCodeComputer.INSTANCE;
            case "unrolledDefault" -> UnrolledDefaultHashCodeComputer.INSTANCE;
            case "nativeHash" -> NativeHashCodeComputer.INSTANCE;
            default -> throw new IllegalArgumentException(hashingStrategyName);
        };
    }

    public static void main(String[] args) throws RunnerException {
        runCountingCollisions();
//        Options opt = new OptionsBuilder()
//                .include(HashMapBenchmark.class.getSimpleName())
//                .build();
//
//        new Runner(opt).run();
    }

    private static void runCountingCollisions() {

        ArrayList<String> hashes = new ArrayList<>();
        hashes.add("xxHash");
        hashes.add("default");
        hashes.add("metroHash");
        hashes.add("unrolledDefault");
        hashes.add("nativeHash");

        for (String hash : hashes) {
            HashMapBenchmark bench = new HashMapBenchmark();
            bench.loadFactor = 50;
            bench.maxInactiveOrdersPerSource = 4096;
            bench.activeDataTotal = (1 << 20);
            bench.orderIdHashingStrategy = hash;
            bench.init();

            for (int i = 0; i < (1 << 25); i++) {
                bench.prepareMessages();
                bench.newDataPlusDeactivate();
            }
            System.out.println("Collisions count for " + hash + ": " + bench.map.getCollisions());
        }
    }

    public static final class KeyNamingStrategy {

        private final int sourceCount;
        private final String[] sourcesOrderIdPrefix;

        private KeyNamingStrategy(int sourceCount) {
            this.sourceCount = sourceCount;
            this.sourcesOrderIdPrefix = new String[sourceCount];
            for (int i = 0; i < sourceCount; i++) {
                long sourceId = MAGIC_CONSTANT;
                this.sourcesOrderIdPrefix[i] = Long.toString(sourceId, 32) + ':';
            }
        }

        public void formatKey(long orderId, AsciiString result) {
            final int sourceIndex = (int) (orderId % sourceCount);
            result.setString(sourcesOrderIdPrefix[sourceIndex] + orderId);
        }
    }
}
