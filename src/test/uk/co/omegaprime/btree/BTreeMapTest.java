package uk.co.omegaprime.btree;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.*;

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

    public static class FloorEntry extends KeyedOperation<Map.Entry<String, Integer>> {
        public FloorEntry(String key) { super("FloorEntry", key, NavigableMap::floorEntry); }
    }

    public static class HigherEntry extends KeyedOperation<Map.Entry<String, Integer>> {
        public HigherEntry(String key) { super("HigherEntry", key, NavigableMap::higherEntry); }
    }

    public static class CeilingEntry extends KeyedOperation<Map.Entry<String, Integer>> {
        public CeilingEntry(String key) { super("CeilingEntry", key, NavigableMap::ceilingEntry); }
    }

    // Actually a test of our ceilingIterator
    public static class TailMap extends KeyedOperation<Collection<Integer>> {
        public TailMap(String key) { super("TailMap", key, (m, k) -> new ArrayList<>(m.tailMap(k, true).values())); }
    }

    // Actually a test of our higherIterator
    public static class TailMapExclusive extends KeyedOperation<Collection<Integer>> {
        public TailMapExclusive(String key) { super("TailMapExclusive", key, (m, k) -> new ArrayList<>(m.tailMap(k, false).values())); }
    }

    // Actually a test of our floorIterator
    public static class DescendingTailMap extends KeyedOperation<Collection<Integer>> {
        public DescendingTailMap(String key) { super("DescendingTailMap", key, (m, k) -> new ArrayList<>(m.descendingMap().tailMap(k, true).values())); }
    }

    // Actually a test of our lowerIterator
    public static class DescendingTailMapExclusive extends KeyedOperation<Collection<Integer>> {
        public DescendingTailMapExclusive(String key) { super("DescendingTailMapExclusive", key, (m, k) -> new ArrayList<>(m.descendingMap().tailMap(k, false).values())); }
    }

    public static class HeadMap extends KeyedOperation<Collection<Integer>> {
        public HeadMap(String key) { super("HeadMap", key, (m, k) -> new ArrayList<>(m.headMap(k, true).values())); }
    }

    public static class HeadMapExclusive extends KeyedOperation<Collection<Integer>> {
        public HeadMapExclusive(String key) { super("HeadMapExclusive", key, (m, k) -> new ArrayList<>(m.headMap(k, false).values())); }
    }

    public static class UnkeyedOperation<T> implements Operation {
        private final String name;
        private final Function<NavigableMap<String, Integer>, T> f;

        public UnkeyedOperation(String name, Function<NavigableMap<String, Integer>, T> f) {
            this.name = name;
            this.f = f;
        }

        @Override
        public void apply(TreeMap<String, Integer> expected, BTreeMap<String, Integer> actual) {
            Assert.assertEquals(f.apply(expected), f.apply(actual));
        }

        @Override
        public String toString() { return name; }
    }

    public static class Size extends UnkeyedOperation<Integer> {
        public Size() { super("Size", NavigableMap::size); }
    }

    // Actually a test of our ascending iterator
    public static class Values extends UnkeyedOperation<Collection<Integer>> {
        public Values() { super("Values", m -> new ArrayList<>(m.values())); }
    }

    // Actually a test of our descending iterator
    public static class DescendingValues extends UnkeyedOperation<Collection<Integer>> {
        public DescendingValues() { super("DescendingValues", m -> new ArrayList<>(m.descendingMap().values())); }
    }

    public static class FirstEntry extends UnkeyedOperation<Map.Entry<String, Integer>> {
        public FirstEntry() { super("FirstEntry", NavigableMap::firstEntry); }
    }

    public static class LastEntry extends UnkeyedOperation<Map.Entry<String, Integer>> {
        public LastEntry() { super("LastEntry", NavigableMap::lastEntry); }
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

    private static String randomKey(SourceOfRandomness sor) {
        // Use a small keyspace so that we'll randomly get some collisions. Tests more interesting that way!
        final int length = sor.nextInt(1, 3);
        final char[] cs = new char[length];
        for (int i = 0; i < length; i++) {
            cs[i] = (char)('a' + sor.nextInt('z' - 'a'));
        }

        return new String(cs);
    }

    public static class OperationGenerator extends Generator<Operation> {
        public OperationGenerator() {
            super(Operation.class);
        }

        @Override
        public Operation generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
            switch (sourceOfRandomness.nextInt(17)) {
                case 0:  return new Put(randomKey(sourceOfRandomness), sourceOfRandomness.nextInt());
                case 1:  return new Get(randomKey(sourceOfRandomness));
                case 2:  return new LowerEntry(randomKey(sourceOfRandomness));
                case 3:  return new FloorEntry(randomKey(sourceOfRandomness));
                case 4:  return new HigherEntry(randomKey(sourceOfRandomness));
                case 5:  return new CeilingEntry(randomKey(sourceOfRandomness));
                case 6:  return new Size();
                case 7:  return new FirstEntry();
                case 8:  return new LastEntry();
                case 9:  return new Values();
                case 10: return new DescendingValues();
                case 11: return new TailMap(randomKey(sourceOfRandomness));
                case 12: return new TailMapExclusive(randomKey(sourceOfRandomness));
                case 13: return new HeadMap(randomKey(sourceOfRandomness));
                case 14: return new HeadMapExclusive(randomKey(sourceOfRandomness));
                case 15: return new DescendingTailMap(randomKey(sourceOfRandomness));
                case 16: return new DescendingTailMapExclusive(randomKey(sourceOfRandomness));
                default: throw new IllegalStateException();
            }
        }
    }

    @Property
    public void randomOperationSequenceOnEmptyMap(@com.pholser.junit.quickcheck.generator.Size(min=4, max=100) List<@From(OperationGenerator.class) Operation> ops) {
        final TreeMap<String, Integer> expected = new TreeMap<>();
        final BTreeMap<String, Integer> actual = BTreeMap.create();

        for (Operation op : ops) {
            op.apply(expected, actual);
            actual.check();
        }
    }

    @Property(trials = 1000)
    public void randomOperationSequenceOnBigMap(@com.pholser.junit.quickcheck.generator.Size(min=4, max=100) List<@From(OperationGenerator.class) Operation> ops) {
        final TreeMap<String, Integer> expected = new TreeMap<>();
        final BTreeMap<String, Integer> actual = BTreeMap.create();

        // Try to make sure we have at least one internal node
        final SourceOfRandomness sor = new SourceOfRandomness(new Random(1337));
        for (int i = 0; i < 128; i++) {
            final String key = randomKey(sor);
            expected.put(key, i);
            actual  .put(key, i);
        }

        for (Operation op : ops) {
            op.apply(expected, actual);
            actual.check();
        }
    }

    @Test
    public void lowerEntry() {
        final BTreeMap<Integer, String> map = BTreeMap.create();
        map.put(1, "One");
        map.put(3, "Three");

        assertEquals(null, map.lowerEntry(0));
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

    @Test
    public void descendingIterator1Item() {
        final BTreeMap<String, Integer> map = BTreeMap.create();
        map.put("Hello", 123);

        final Iterator<Map.Entry<String, Integer>> it = map.descendingEntrySet().iterator();

        {
            assertTrue(it.hasNext());
            final Map.Entry<String, Integer> e = it.next();
            assertEquals("Hello", e.getKey());
            assertEquals(Integer.valueOf(123), e.getValue());
        }

        assertFalse(it.hasNext());
    }

    @Test
    public void descendingIterator2Items() {
        final BTreeMap<String, Integer> map = BTreeMap.create();
        map.put("Hello", 123);
        map.put("World", 321);

        final Iterator<Map.Entry<String, Integer>> it = map.descendingEntrySet().iterator();

        {
            assertTrue(it.hasNext());
            final Map.Entry<String, Integer> e = it.next();
            assertEquals("World", e.getKey());
            assertEquals(Integer.valueOf(321), e.getValue());
        }

        {
            assertTrue(it.hasNext());
            final Map.Entry<String, Integer> e = it.next();
            assertEquals("Hello", e.getKey());
            assertEquals(Integer.valueOf(123), e.getValue());
        }

        assertFalse(it.hasNext());
    }

    @Test
    public void tailMap() {
        final BTreeMap<String, Integer> map = BTreeMap.create();
        for (int i = 11; i < 99; i += 2) {
            map.put(Integer.toString(i), i);
        }

        for (int i = 10; i < 100; i++) {
            int j = i % 2 == 1 ? i : i + 1;
            for (Integer x : map.tailMap(Integer.toString(i), true).values()) {
                assertEquals(j, x.intValue());
                j += 2;
            }
            assertEquals(99, j);
        }
    }

    @Test
    public void tailMapExclusive() {
        final BTreeMap<String, Integer> map = BTreeMap.create();
        for (int i = 11; i < 99; i += 2) {
            map.put(Integer.toString(i), i);
        }

        for (int i = 10; i < 100; i++) {
            int j = Math.min(99, i % 2 == 1 ? i + 2 : i + 1);
            for (Integer x : map.tailMap(Integer.toString(i), false).values()) {
                assertEquals(j, x.intValue());
                j += 2;
            }
            assertEquals(99, j);
        }
    }

    @Test
    public void descendingTailMap() {
        final BTreeMap<String, Integer> map = BTreeMap.create();
        for (int i = 11; i < 99; i += 2) {
            map.put(Integer.toString(i), i);
        }

        for (int i = 10; i < 100; i++) {
            int j = Math.min(97, i % 2 == 1 ? i : i - 1);
            for (Integer x : map.descendingMap().tailMap(Integer.toString(i), true).values()) {
                assertEquals(j, x.intValue());
                j -= 2;
            }
            assertEquals(9, j);
        }
    }

    @Test
    public void descendingTailMapExclusive() {
        final BTreeMap<String, Integer> map = BTreeMap.create();
        for (int i = 11; i < 99; i += 2) {
            map.put(Integer.toString(i), i);
        }

        for (int i = 10; i < 100; i++) {
            int j = i % 2 == 1 ? i - 2 : i - 1;
            for (Integer x : map.descendingMap().tailMap(Integer.toString(i), false).values()) {
                assertEquals(j, x.intValue());
                j -= 2;
            }
            assertEquals(9, j);
        }
    }
}
