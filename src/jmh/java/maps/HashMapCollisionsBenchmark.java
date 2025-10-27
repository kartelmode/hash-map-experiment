package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import internal.DataPayload;
import org.openjdk.jmh.runner.RunnerException;

import java.security.Key;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

import static maps.HashMapBenchmark.selectAsciiHashCodeComputer;

/** not really a JMH test :-) (extracted from main JMH test) */
public class HashMapCollisionsBenchmark {

    public static void main(String[] args) throws RunnerException {
        final String[] keyNamingStrategies = {
                "number",
                "mm",
                "guid"
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

        final int maxNameLen = Arrays.stream(hashStrategies)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        String resultsFormat = "Hash %-" + maxNameLen + "s produces %12d collisions for key pattern %10s%n";
        final int maxActiveKeys = 1048576;
        final int maxInactiveKeys = 4096;
        for (String keyNaming : keyNamingStrategies) {
            final KeyNamingStrategy keyNamingStrategy = KeyNamingStrategy.select(keyNaming);
            for (String hashStrategy : hashStrategies) {
                final Deque<DataPayload> activeSet = new ArrayDeque<>(maxActiveKeys);
                final int cacheCapacity = 2 * maxActiveKeys;
                final HashCodeComputer hash = selectAsciiHashCodeComputer(hashStrategy);
                final Cache cache = new ChainingHashMap(cacheCapacity, maxInactiveKeys, hash);

                for (int i = 0; i < (1 << 25); i++) {
                    if (i >= maxActiveKeys) {
                        DataPayload oldestActive = activeSet.removeFirst(); // head
                        cache.deactivate(oldestActive);
                    }

                    DataPayload newest = new DataPayload(new AsciiString(keyNamingStrategy.formatKey(i))); //TODO: i+BASE
                    cache.putIfEmpty(newest);
                    activeSet.add(newest); // tail

                }
                System.out.printf(resultsFormat, hashStrategy, cache.collisionCount(), keyNaming);
            }
        }
    }
}
