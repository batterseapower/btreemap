package uk.co.omegaprime.btree;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.generator.java.util.function.BiFunctionGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class BTreeMapTest {
    private interface Operation {
        void apply(TreeMap<String, Integer> expected, BTreeMap<String, Integer> actual);
    }

    public abstract static class KeyedOperation<T> implements Operation {
        private final String name;
        private final String key;
        private final BiFunction<NavigableMap<String, Integer>, String, T> f;

        public KeyedOperation(String name, String key, BiFunction<NavigableMap<String, Integer>, String, T> f) {
            this.name = name;
            this.key = key;
            this.f = f;
        }

        @Override
        public void apply(TreeMap<String, Integer> expected, BTreeMap<String, Integer> actual) {
            Assert.assertEquals(f.apply(expected, key), f.apply(actual, key));
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", name, key);
        }
    }

    public static class Get extends KeyedOperation<Integer> {
        public Get(String key) { super("Get", key, Map::get); }
    }

    public static class LowerEntry extends KeyedOperation<Map.Entry<String, Integer>> {
        public LowerEntry(String key) { super("LowerEntry", key, NavigableMap::lowerEntry); }
    }

    public static class Size implements Operation {
        @Override
        public void apply(TreeMap<String, Integer> expected, BTreeMap<String, Integer> actual) {
            Assert.assertEquals(expected.size(), actual.size());
        }

        @Override
        public String toString() { return "Size"; }
    }

    public static class Put implements Operation {
        public final String key;
        public final int value;
        public Put(String key, int value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public void apply(TreeMap<String, Integer> expected, BTreeMap<String, Integer> actual) {
            Assert.assertEquals(expected.put(key, value), actual.put(key, value));
        }

        @Override
        public String toString() {
            return String.format("Put(%s, %s)", key, value);
        }
    }

    public static class OperationGenerator extends Generator<Operation> {
        public OperationGenerator() {
            super(Operation.class);
        }

        @Override
        public Operation generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
            switch (sourceOfRandomness.nextInt(3)) {
                case 0: return new Get(new StringGenerator().generate(sourceOfRandomness, generationStatus));
                case 1: return new Put(new StringGenerator().generate(sourceOfRandomness, generationStatus),
                                       sourceOfRandomness.nextInt());
                case 2: return new Size();
                default: throw new IllegalStateException();
            }
        }
    }

    @Property
    public void randomOperationSequence(@com.pholser.junit.quickcheck.generator.Size(min=4, max=100) List<@From(OperationGenerator.class) Operation> ops) {
        final TreeMap<String, Integer> expected = new TreeMap<>();
        final BTreeMap<String, Integer> actual = BTreeMap.create();

        for (Operation op : ops) {
            op.apply(expected, actual);
        }
    }

    @Test
    public void lowerEntry() {
        final BTreeMap<Integer, String> map = BTreeMap.create();
        map.put(1, "One");
        map.put(3, "Three");

        assertEquals(null, map.lowerEntry(1));
        assertEquals(Integer.valueOf(1), map.lowerEntry(2).getKey());
        assertEquals("One",              map.lowerEntry(2).getValue());
        assertEquals(Integer.valueOf(3), map.lowerEntry(4).getKey());
        assertEquals("Three",            map.lowerEntry(4).getValue());
    }

    @Test
    public void putManyCheckingWithGet() {
        final TreeMap<Integer, Integer> expected = new TreeMap<>();
        final BTreeMap<Integer, Integer> actual = BTreeMap.create();

        final int[] keys = new Random(1337).ints(0, 100).limit(100).toArray();
        int i = 0;
        for (int key : keys) {
            expected.put(key, i);
            actual.put(key, i);
            i++;

            for (Map.Entry<Integer, Integer> e : expected.entrySet()) {
                Assert.assertEquals(e.getValue(), actual.get(e.getKey()));
            }
        }
    }

    // Mostly useful for finding out whether this crashes or not
    @Test
    public void putLinear() {
        final int KEYS = 100_000;

        final Random random = new Random(1337);
        final BTreeMap<Integer, Integer> map = BTreeMap.create();
        for (int i = 0; i < KEYS; i++) {
            map.put(random.nextInt(KEYS), i);
        }

        assertEquals(map.get(-1), null);
    }
}
