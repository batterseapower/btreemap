package uk.co.omegaprime.btree;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openjdk.jmh.Main;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class BTreeMapTest {
    private interface Operation {
        void apply(TreeMap<String, Integer> expected, BTreeMap<String, Integer> actual);
    }

    public static class Get implements Operation {
        public final String key;
        public Get(String key) { this.key = key; }

        @Override
        public void apply(TreeMap<String, Integer> expected, BTreeMap<String, Integer> actual) {
            Assert.assertEquals(expected.get(key), actual.get(key));
        }

        @Override
        public String toString() {
            return String.format("Get(%s)", key);
        }
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
