package maps;

import hashing.HashCodeComputer;
import internal.AsciiString;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class HashCodeComputerBenchmark {

    @Param({
            "hashing.DefaultHashCodeComputer",
            "hashing.FasterHashCodeComputer",
            "hashing.MetroHashCodeComputer",
            "hashing.NativeHashCodeComputer",
            "hashing.UnrolledDefaultHashCodeComputer",
            "hashing.vhHashCodeComputer",
            "hashing.XxHashCodeComputer",
            "hashing.vhFasterHashCodeComputer"
    })
    private String hashCodeComputerClassName;

    @Param({"8", "16", "32"})
    private int stringLength;

    private HashCodeComputer hashCodeComputer;
    private AsciiString testString;

    private HashCodeComputer instantiateHashCodeComputer(String className) throws Exception {
        Class<?> clazz = Class.forName(className);

        Field instanceField = clazz.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);
        return (HashCodeComputer) instance;
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        hashCodeComputer = instantiateHashCodeComputer(hashCodeComputerClassName);

        StringBuilder sb = new StringBuilder(stringLength);
        for (int i = 0; i < stringLength; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        testString = new AsciiString(sb.toString());
    }

    @Benchmark
    public int benchmarkHashCode() {
        return hashCodeComputer.hashCode(testString);
    }

    @Benchmark
    public int benchmarkModPowerOfTwoHashCode() {
        return hashCodeComputer.modPowerOfTwoHashCode(testString, 256);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(HashCodeComputerBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}