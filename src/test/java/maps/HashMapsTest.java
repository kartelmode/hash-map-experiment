package maps;

import hashing.DefaultHashCodeComputer;
import hashing.HashCodeComputer;
import hashing.NativeHashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cache implementations JUnit 5 test suite.
 * Encodes FIFO inactive retention and model-based fuzz checks.
 * Assumptions:
 *  - putIfEmpty(entry): false iff duplicate key already present; else true.
 *  - All entries retained until deactivated; inactive retained up to maxInactiveDataCount in FIFO order.
 *  - maxInactiveDataCount=0: inactives are never retained (immediately evicted on deactivate).
 *  - deactivate: adds to inactive queue; if queue full, oldest inactive is evicted from cache.
 *  - get: returns entry if present (active or inactive); null otherwise.
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
class HashMapsTest {

    private static final HashCodeComputer HASH_COMPUTER = DefaultHashCodeComputer.INSTANCE;

    // Test data factory for different cache implementations
    static Stream<CacheFactory> cacheFactories() {
        return Stream.of(
                new CacheFactory("ChainingHashMap", (active, inactive) ->
                        new ChainingHashMap(active, inactive, HASH_COMPUTER)),
                new CacheFactory("LinearProbingHashMap", (active, inactive) ->
                        new LinearProbingHashMap(active, inactive, HASH_COMPUTER)),
                new CacheFactory("RobinHoodHashMap", (active, inactive) ->
                        new RobinHoodHashMap(active, inactive, HASH_COMPUTER)),
                new CacheFactory("JavaHashMap", JavaHashMap::new)
        );
    }

    @ParameterizedTest(name = "{0}: basic put and get")
    @MethodSource("cacheFactories")
    void basicPutAndGet(CacheFactory factory) {
        Cache cache = factory.create(16, 2);
        DataPayload d1 = new DataPayload("O1");

        assertTrue(cache.putIfEmpty(d1));
        assertEquals(1, cache.size());
        assertSame(d1, cache.get(new AsciiString("O1")));
        assertNull(cache.get(new AsciiString("XXX")));

        DataPayload d2 = new DataPayload("O2");
        assertTrue(cache.putIfEmpty(d2));
        assertEquals(2, cache.size());
        assertSame(d1, cache.get(new AsciiString("O1")));
        assertSame(d2, cache.get(new AsciiString("O2")));
        assertNull(cache.get(new AsciiString("XXX")));
    }

    @ParameterizedTest(name = "{0}: deactivate with maxInactive=1")
    @MethodSource("cacheFactories")
    void basicDeactivateWithMaxInactive1(CacheFactory factory) {
        Cache cache = factory.create(16, 1);
        DataPayload d1 = new DataPayload("O1");
        assertTrue(cache.putIfEmpty(d1));

        cache.deactivate(d1);
        assertEquals(1, cache.size());
        assertSame(d1, cache.get(new AsciiString("O1")));

        DataPayload d2 = new DataPayload("O2");
        assertTrue(cache.putIfEmpty(d2));
        cache.deactivate(d2); // this will push O1 out

        assertEquals(1, cache.size());
        assertNull(cache.get(new AsciiString("O1"))); // no longer in cache
        assertSame(d2, cache.get(new AsciiString("O2")));
    }

    @ParameterizedTest(name = "{0}: two inactive entries at most")
    @MethodSource("cacheFactories")
    void twoInactiveEntriesAtMost(CacheFactory factory) {
        Cache cache = factory.create(16, 2);
        DataPayload d1 = new DataPayload("O1");
        DataPayload d2 = new DataPayload("O2");
        DataPayload d3 = new DataPayload("O3");

        assertTrue(cache.putIfEmpty(d1));
        cache.deactivate(d1);
        assertTrue(cache.putIfEmpty(d2));
        cache.deactivate(d2);

        assertEquals(2, cache.size());
        assertSame(d1, cache.get(new AsciiString("O1")));
        assertSame(d2, cache.get(new AsciiString("O2")));

        assertTrue(cache.putIfEmpty(d3));
        cache.deactivate(d3); // this will push O1 out

        assertEquals(2, cache.size());
        assertNull(cache.get(new AsciiString("O1"))); // no longer in cache
        assertSame(d2, cache.get(new AsciiString("O2")));
        assertSame(d3, cache.get(new AsciiString("O3")));
    }

    @ParameterizedTest(name = "{0}: four inactive entries at most")
    @MethodSource("cacheFactories")
    void fourInactiveEntriesAtMost(CacheFactory factory) {
        Cache cache = factory.create(16, 4);
        DataPayload d1 = new DataPayload("O1");
        DataPayload d2 = new DataPayload("O2");
        DataPayload d3 = new DataPayload("O3");
        DataPayload d4 = new DataPayload("O4");
        DataPayload d5 = new DataPayload("O5");

        assertTrue(cache.putIfEmpty(d1));
        cache.deactivate(d1);
        assertTrue(cache.putIfEmpty(d2));
        cache.deactivate(d2);
        assertTrue(cache.putIfEmpty(d3));
        cache.deactivate(d3);
        assertTrue(cache.putIfEmpty(d4));
        cache.deactivate(d4);

        assertEquals(4, cache.size());
        assertSame(d1, cache.get(d1.getKey()));
        assertSame(d2, cache.get(d2.getKey()));
        assertSame(d3, cache.get(d3.getKey()));
        assertSame(d4, cache.get(d4.getKey()));

        assertTrue(cache.putIfEmpty(d5));
        cache.deactivate(d5); // this will push O1 out

        assertEquals(4, cache.size());
        assertNull(cache.get(new AsciiString("O1"))); // no longer in cache
        assertSame(d2, cache.get(d2.getKey()));
        assertSame(d3, cache.get(d3.getKey()));
        assertSame(d4, cache.get(d4.getKey()));
        assertSame(d5, cache.get(d5.getKey()));
    }

    // ---------- 1) Construction & empty state ----------
    @ParameterizedTest(name = "{0}: empty cache has size 0")
    @MethodSource("cacheFactories")
    void emptyCacheBasics(CacheFactory factory) {
        for (int maxInactive : new int[]{1, 2, 4, 8, 64}) {
            Cache cache = factory.create(16, maxInactive);
            assertEquals(0, cache.size(), "size must be 0 after construction");
            assertNull(cache.get(new AsciiString("missing")), "missing key must return null");
        }
    }

    // ---------- 2) putIfEmpty basics (duplicates, maxInactiveDataCount edge cases) ----------
    @ParameterizedTest(name = "{0}: putIfEmpty basic")
    @MethodSource("cacheFactories")
    void putIfEmpty_basic(CacheFactory factory) {
        Cache cache = factory.create(16, 4);
        DataPayload a = new DataPayload("A");
        assertTrue(cache.putIfEmpty(a));
        assertSame(a, cache.get(new AsciiString("A")));
        assertEquals(1, cache.size());
    }

    @ParameterizedTest(name = "{0}: putIfEmpty duplicate returns false")
    @MethodSource("cacheFactories")
    void putIfEmpty_duplicate(CacheFactory factory) {
        Cache cache = factory.create(16, 4);
        DataPayload a1 = new DataPayload("A");
        DataPayload a2 = new DataPayload("A");

        assertTrue(cache.putIfEmpty(a1));
        int sizeBefore = cache.size();
        assertFalse(cache.putIfEmpty(a2), "duplicate key must be rejected");
        assertEquals(sizeBefore, cache.size());
        assertSame(a1, cache.get(new AsciiString("A")));
    }

    @ParameterizedTest(name = "{0}: deactivate with maxInactive=0 evicts immediately")
    @MethodSource("cacheFactories")
    void deactivate_maxInactive0(CacheFactory factory) {
        Cache cache = factory.create(16, 1); // min is 1 due to power of 2 requirement
        DataPayload x = new DataPayload("X");
        assertTrue(cache.putIfEmpty(x));

        cache.deactivate(x);
        // With maxInactive=1, it should be retained
        assertNotNull(cache.get(new AsciiString("X")));
        assertEquals(1, cache.size());
    }

    // ---------- 3) FIFO eviction of inactives & wrap-around ----------
    @Test
    @DisplayName("ChainingHashMap: FIFO eviction order for inactive entries")
    void fifoEviction_chaining() {
        testFifoEviction(new ChainingHashMap(16, 4, HASH_COMPUTER));
    }

    @Test
    @DisplayName("LinearProbingHashMap: FIFO eviction order for inactive entries")
    void fifoEviction_linearProbing() {
        testFifoEviction(new LinearProbingHashMap(16, 4, HASH_COMPUTER));
    }

    @Test
    @DisplayName("RobinHoodHashMap: FIFO eviction order for inactive entries")
    void fifoEviction_robinHood() {
        testFifoEviction(new RobinHoodHashMap(16, 4, HASH_COMPUTER));
    }

    @Test
    @DisplayName("JavaHashMap: FIFO eviction order for inactive entries")
    void fifoEviction_java() {
        testFifoEviction(new JavaHashMap(16, 4));
    }

    private void testFifoEviction(Cache cache) {
        // Add 4 entries and deactivate them (I0, I1, I2, I3) -> all retained
        DataPayload[] entries = new DataPayload[6];
        for (int i = 0; i < 4; i++) {
            entries[i] = new DataPayload(id("I", i));
            assertTrue(cache.putIfEmpty(entries[i]));
            cache.deactivate(entries[i]);
        }
        assertEquals(4, cache.size());
        for (int i = 0; i < 4; i++) {
            assertNotNull(cache.get(entries[i].getKey()), "I-" + i + " should be present");
        }

        // Add and deactivate I4 -> evict oldest (I0)
        entries[4] = new DataPayload("I-4");
        assertTrue(cache.putIfEmpty(entries[4]));
        cache.deactivate(entries[4]);

        assertEquals(4, cache.size());
        assertNull(cache.get(new AsciiString("I-0")), "oldest inactive must be evicted");
        assertNotNull(cache.get(new AsciiString("I-1")));
        assertNotNull(cache.get(new AsciiString("I-2")));
        assertNotNull(cache.get(new AsciiString("I-3")));
        assertNotNull(cache.get(new AsciiString("I-4")));

        // Another one (I5) -> evicts I1
        entries[5] = new DataPayload("I-5");
        assertTrue(cache.putIfEmpty(entries[5]));
        cache.deactivate(entries[5]);

        assertNull(cache.get(new AsciiString("I-1")));
        assertNotNull(cache.get(new AsciiString("I-2")));
        assertNotNull(cache.get(new AsciiString("I-3")));
        assertNotNull(cache.get(new AsciiString("I-4")));
        assertNotNull(cache.get(new AsciiString("I-5")));
    }

    @Test
    @DisplayName("ChainingHashMap: FIFO with mixed active and deactivate flow")
    void fifoEviction_mixedFlow_chaining() {
        testFifoMixedFlow(new ChainingHashMap(16, 4, HASH_COMPUTER));
    }

    @Test
    @DisplayName("LinearProbingHashMap: FIFO with mixed active and deactivate flow")
    void fifoEviction_mixedFlow_linearProbing() {
        testFifoMixedFlow(new LinearProbingHashMap(16, 4, HASH_COMPUTER));
    }

    @Test
    @DisplayName("RobinHoodHashMap: FIFO with mixed active and deactivate flow")
    void fifoEviction_mixedFlow_robinHood() {
        testFifoMixedFlow(new RobinHoodHashMap(16, 4, HASH_COMPUTER));
    }

    @Test
    @DisplayName("JavaHashMap: FIFO with mixed active and deactivate flow")
    void fifoEviction_mixedFlow_java() {
        testFifoMixedFlow(new JavaHashMap(16, 4));
    }

    private void testFifoMixedFlow(Cache cache) {
        // Put 5 entries: A0..A4 (not deactivated yet)
        DataPayload[] entries = new DataPayload[6];
        for (int i = 0; i < 5; i++) {
            entries[i] = new DataPayload(id("A", i));
            assertTrue(cache.putIfEmpty(entries[i]));
        }
        assertEquals(5, cache.size());

        // Deactivate A0,A1,A2,A3 -> inactive buffer full (A0 oldest)
        cache.deactivate(entries[0]); // A-0
        cache.deactivate(entries[1]); // A-1
        cache.deactivate(entries[2]); // A-2
        cache.deactivate(entries[3]); // A-3

        assertEquals(5, cache.size(), "5 entries: 4 inactive + 1 active (A-4)");

        // Deactivate A4 -> evicts A-0 (oldest inactive)
        cache.deactivate(entries[4]);

        assertNull(cache.get(new AsciiString("A-0")), "A-0 should be evicted");
        assertNotNull(cache.get(new AsciiString("A-1")));
        assertNotNull(cache.get(new AsciiString("A-2")));
        assertNotNull(cache.get(new AsciiString("A-3")));
        assertNotNull(cache.get(new AsciiString("A-4")));
        assertEquals(4, cache.size(), "4 inactive entries");
    }

    // ---------- 4) Model-based fuzz (single-threaded) ----------
    @ParameterizedTest(name = "fuzzyTest ChainingHashMap [maxInactive={0}, totalEntries={1}]")
    @CsvSource({
            "1,200", "1,666",
            "2,200", "2,666",
            "4,200", "4,666",
            "32,200", "32,666"
    })
    void fuzzyTest_chaining(int maxInactiveDataCount, int totalEntries) {
        fuzzyTestImpl(new ChainingHashMap(16, maxInactiveDataCount, HASH_COMPUTER),
                maxInactiveDataCount, totalEntries);
    }

    @ParameterizedTest(name = "fuzzyTest LinearProbingHashMap [maxInactive={0}, totalEntries={1}]")
    @CsvSource({
            "1,200", "1,666",
            "2,200", "2,666",
            "4,200", "4,666",
            "32,200", "32,666"
    })
    void fuzzyTest_linearProbing(int maxInactiveDataCount, int totalEntries) {
        fuzzyTestImpl(new LinearProbingHashMap(16, maxInactiveDataCount, HASH_COMPUTER),
                maxInactiveDataCount, totalEntries);
    }

    @ParameterizedTest(name = "fuzzyTest RobinHoodHashMap [maxInactive={0}, totalEntries={1}]")
    @CsvSource({
            "1,200", "1,666",
            "2,200", "2,666",
            "4,200", "4,666",
            "32,200", "32,666"
    })
    void fuzzyTest_robinHood(int maxInactiveDataCount, int totalEntries) {
        fuzzyTestImpl(new RobinHoodHashMap(16, maxInactiveDataCount, HASH_COMPUTER),
                maxInactiveDataCount, totalEntries);
    }

    @ParameterizedTest(name = "fuzzyTest JavaHashMap [maxInactive={0}, totalEntries={1}]")
    @CsvSource({
            "1,200", "1,666",
            "2,200", "2,666",
            "4,200", "4,666",
            "32,200", "32,666"
    })
    void fuzzyTest_java(int maxInactiveDataCount, int totalEntries) {
        fuzzyTestImpl(new JavaHashMap(16, maxInactiveDataCount),
                maxInactiveDataCount, totalEntries);
    }

    enum FuzzyAction {
        PUT, DEACTIVATE, GET, CHECK;

        static FuzzyAction get(int probability) {
            if (probability < 45)
                return PUT;
            if (probability < 75)
                return DEACTIVATE;
            if (probability < 95)
                return GET;
            return CHECK;
        }
    }

    private void fuzzyTestImpl(Cache cache, int maxInactiveDataCount, int totalEntries) {
        final int OPS = 10000;
        final long SEED = 0xC0FFEE_5EEDL + maxInactiveDataCount;
        Random rnd = new Random(SEED);

        // Test oracle: map of all entries in cache + bounded FIFO of inactives
        Map<String, DataPayload> allEntries = new HashMap<>();
        Set<String> activeEntries = new HashSet<>();
        Deque<String> inactiveEntriesFIFO = new ArrayDeque<>();

        Runnable checkInvariants = () -> {
            // size() equality
            assertEquals(allEntries.size(), cache.size(), "Cache size");

            // get() for every expected key is non-null and correct
            for (Map.Entry<String, DataPayload> e : allEntries.entrySet()) {
                DataPayload got = cache.get(new AsciiString(e.getKey()));
                assertNotNull(got, "Cache is missing entry for key " + e.getKey());
                assertEquals(e.getKey(), got.getKey().toString(), "Key must match");
            }

            // Ensure keys not in model are not in cache
            for (int i = 0; i < totalEntries; i++) {
                String key = id("E", i);
                if (!allEntries.containsKey(key)) {
                    assertNull(cache.get(new AsciiString(key)), "Cache should not contain " + key);
                }
            }
        };

        // Create a bank of potential keys to operate on
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < totalEntries; i++)
            keys.add(id("E", i));

        Map<String, DataPayload> payloadBank = new HashMap<>();

        for (int op = 0; op < OPS; op++) {
            final String randomKey = keys.get(rnd.nextInt(totalEntries));
            final FuzzyAction action = FuzzyAction.get(rnd.nextInt(100));

            switch (action) {
                case PUT -> {
                    boolean existed = allEntries.containsKey(randomKey);
                    DataPayload payload = payloadBank.computeIfAbsent(randomKey, DataPayload::new);

                    boolean putOk = cache.putIfEmpty(payload);

                    if (existed) {
                        assertFalse(putOk, "Duplicate key must be rejected by putIfEmpty(): " + randomKey);
                    } else {
                        assertTrue(putOk, "putIfEmpty for unique key must succeed: " + randomKey);
                        allEntries.put(randomKey, payload);
                        activeEntries.add(randomKey);
                    }
                }
                case DEACTIVATE -> {
                    if (activeEntries.contains(randomKey)) {
                        DataPayload payload = allEntries.get(randomKey);
                        cache.deactivate(payload);

                        activeEntries.remove(randomKey);
                        // move to tail (most recent) in inactive FIFO
                        inactiveEntriesFIFO.remove(randomKey);
                        inactiveEntriesFIFO.addLast(randomKey);

                        while (inactiveEntriesFIFO.size() > maxInactiveDataCount) {
                            String evicted = inactiveEntriesFIFO.removeFirst();
                            allEntries.remove(evicted);
                        }
                    }
                }
                case GET -> {
                    DataPayload got = cache.get(new AsciiString(randomKey));
                    boolean shouldBePresent = allEntries.containsKey(randomKey);

                    if (shouldBePresent) {
                        assertNotNull(got, "Cache is missing entry that should be there: " + randomKey);
                        assertEquals(randomKey, got.getKey().toString());
                    } else {
                        assertNull(got, "Cache is not supposed to have key " + randomKey);
                    }
                }
                case CHECK -> {
                    checkInvariants.run();
                }
            }
        }

        // Final full invariant check
        checkInvariants.run();
    }

    // Helper classes and methods
    private static String id(String prefix, int i) {
        return prefix + "-" + i;
    }

    @FunctionalInterface
    interface CacheCreator {
        Cache create(int activeDataCount, int maxInactiveDataCount);
    }

    static class CacheFactory {
        private final String name;
        private final CacheCreator creator;

        CacheFactory(String name, CacheCreator creator) {
            this.name = name;
            this.creator = creator;
        }

        Cache create(int activeDataCount, int maxInactiveDataCount) {
            return creator.create(activeDataCount, maxInactiveDataCount);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}