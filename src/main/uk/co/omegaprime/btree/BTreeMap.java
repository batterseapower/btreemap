package uk.co.omegaprime.btree;

import java.util.*;

public class BTreeMap<K, V> implements NavigableMap<K, V> {
    public static <K extends Comparable<? super K>, V> BTreeMap<K, V> create() {
        return new BTreeMap<K, V>(null);
    }

    /** @param comparator If null, the natural order of the keys will be used */
    public static <K, V> BTreeMap<K, V> create(Comparator<K> comparator) {
        return new BTreeMap<K, V>(comparator);
    }

    public static <K, V> BTreeMap<K, V> create(SortedMap<K, ? extends V> that) {
        final BTreeMap<K, V> result = new BTreeMap<>(that.comparator());
        result.putAll(that);
        return result;
    }

    public static <K, V> BTreeMap<K, V> create(Map<? extends K, ? extends V> that) {
        final BTreeMap<K, V> result = new BTreeMap<>(null);
        result.putAll(that);
        return result;
    }

    // We're going to do a generalized (2, 3) tree i.e. each internal node will have between m and (2m - 1) children inclusive, for m >= 2
    //
    // What's a sensible value for m? It would be good if each array we allocate fits within one cache line. On Skylake,
    // cache lines are 64 bytes, and with compressed OOPS (default for heap sizes < 32GB) object pointers are only 4 bytes long,
    // implying that MAX_FANOUT = 16 would be a good choice, i.e. MIN_FANOUT = 8.
    //
    // With MIN_FANOUT=2:
    //   Benchmark               Mode  Cnt        Score        Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  1900386.806 ± 115791.569  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  1617089.096 ±  32891.292  ops/s
    //
    // With MIN_FANOUT=8:
    //   Benchmark               Mode  Cnt        Score        Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  4021130.733 ±  31473.315  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  2821784.716 ± 141837.270  ops/s
    //
    // java.util.TreeMap:
    //   Benchmark               Mode  Cnt        Score        Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  3226633.131 ± 195725.464  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  2561772.533 ±  31611.667  ops/s
    private static final int MIN_FANOUT = 8;
    private static final int MAX_FANOUT = 2 * MIN_FANOUT - 1;

    // Each internal node will be represented by:
    //  1. An Object[] keysNodes of size MAX_FANOUT * 2
    //   - The first MAX_FANOUT - 1 elements of this will refer to keys
    //   - The next MAX_FANOUT elements will hold references to the Object[] of child nodes
    //   - The final element will hold a reference to a int[] of child node sizes
    //  2. A primitive int allocated at some position in an int[] (or, for the root node, in the BTreeMap itself).
    //     This primitive int will be strictly negative. It is the number of child nodes that are present.
    //
    // Each leaf node will be represented by:
    //  1. An Object[] keysValues of size MAX_FANOUT * 2
    //   - The first MAX_FANOUT elements will refer to keys
    //   - The next MAX_FANOUT elements will refer to values
    //  2. A primitive int allocated in an int[] (or, for the root node, in the BTreeMap itself).
    //     This primitive int will be non-negative: it is the number of keys that are defined
    //
    // Going from a boxed representation where I had a "Leaf" class and an "Internal" class that
    // held the relevant data, to a representation where I unbox everything these arrays gave a small speedup.
    //
    // Before:
    //   Benchmark               Mode  Cnt        Score        Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  4021130.733 ±  31473.315  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  2821784.716 ± 141837.270  ops/s
    //
    // After:
    //   Benchmark               Mode  Cnt        Score       Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  4054259.892 ± 84642.583  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  3015353.400 ± 80293.635  ops/s


    private static boolean isInternal(int size) {
        return size < 0;
    }

    private static class BubbledInsertion {
        private final Object[] leftObjects, rightObjects;
        private final int leftSize, rightSize;
        private final Object separator; // The seperator key is <= all keys in the right and > all keys in the left

        private BubbledInsertion(Object[] leftObjects, Object[] rightObjects, int leftSize, int rightSize, Object separator) {
            this.leftObjects = leftObjects;
            this.rightObjects = rightObjects;
            this.leftSize = leftSize;
            this.rightSize = rightSize;
            this.separator = separator;
        }
    }

    private static class Leaf {
        private Leaf() {}

        public static String toString(Object[] keysValues, int size) {
            assert size >= 0;

            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < size; i++) {
                if (sb.length() != 0) sb.append(", ");
                sb.append(getKey(keysValues, i)).append(": ").append(getValue(keysValues, i));
            }

            return sb.toString();
        }

        public static int find(Object[] keysValues, int size, Object key, Comparator comparator) {
            assert size >= 0;
            return Arrays.binarySearch(keysValues, 0, size, key, comparator);
        }

        public static Object getKey(Object[] keysValues, int index) {
            return keysValues[index];
        }

        public static Object getValue(Object[] keysValues, int index) {
            return keysValues[MAX_FANOUT + index];
        }

        public static Object putOrDieIfFull(Object[] keysValues, int[] sizeBox, int sizeIndex, Object key, Object value, Comparator comparator) {
            int index = find(keysValues, sizeBox[sizeIndex], key, comparator);
            return putAtIndex(keysValues, sizeBox, sizeIndex, index, key, value);
        }

        public static boolean canPutAtIndex(int size, int index) {
            assert size >= 0;
            return index >= 0 || size < MAX_FANOUT;
        }

        /** @param index must be the index of key in the leaf, using same convention as Arrays.binarySearch */
        public static Object putAtIndex(Object[] keysValues, int[] sizeBox, int sizeIndex, int index, Object key, Object value) {
            assert sizeBox[sizeIndex] >= 0;
            assert canPutAtIndex(sizeBox[sizeIndex], index);

            final Object result;
            if (index < 0) {
                final int size_ = sizeBox[sizeIndex];
                assert size_ < MAX_FANOUT;

                final int insertionPoint = -(index + 1);
                System.arraycopy(keysValues,              insertionPoint, keysValues,              insertionPoint + 1, size_ - insertionPoint);
                System.arraycopy(keysValues, MAX_FANOUT + insertionPoint, keysValues, MAX_FANOUT + insertionPoint + 1, size_ - insertionPoint);
                sizeBox[sizeIndex] = size_ + 1;

                keysValues[insertionPoint] = key;

                result = null;
                index = insertionPoint;
            } else {
                result = keysValues[MAX_FANOUT + index];
            }

            keysValues[MAX_FANOUT + index] = value;
            return result;
        }

        private static void copy(Object[] srcKeysValues, int srcIndex, Object[] dstKeysValues, int dstIndex, int size) {
            System.arraycopy(srcKeysValues, srcIndex,              dstKeysValues, dstIndex,              size);
            System.arraycopy(srcKeysValues, srcIndex + MAX_FANOUT, dstKeysValues, dstIndex + MAX_FANOUT, size);
        }

        // This splits the leaf (of size MAX_FANOUT == 2 * MIN_FANOUT - 1) plus one extra item into two new
        // leaves, each of size MIN_FANOUT.
        public static BubbledInsertion bubblePutAtIndex(Object[] keysValues, int size, int index, Object key, Object value) {
            assert size >= 0;
            assert !canPutAtIndex(size, index);
            assert size == MAX_FANOUT; // i.e. implies index < 0

            int insertionPoint = -(index + 1);
            final Object[] l = new Object[2 * MAX_FANOUT], r = new Object[2 * MAX_FANOUT];

            if (insertionPoint < MIN_FANOUT) {
                copy(keysValues, 0,                           l, 0,                  insertionPoint);
                copy(keysValues, insertionPoint,              l, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);
                copy(keysValues, MIN_FANOUT - 1,              r, 0,                  MIN_FANOUT);

                l[insertionPoint]              = key;
                l[insertionPoint + MAX_FANOUT] = value;
            } else {
                insertionPoint -= MIN_FANOUT;

                copy(keysValues, 0,                           l, 0,                  MIN_FANOUT);
                copy(keysValues, MIN_FANOUT,                  r, 0,                  insertionPoint);
                copy(keysValues, MIN_FANOUT + insertionPoint, r, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);

                r[insertionPoint]              = key;
                r[insertionPoint + MAX_FANOUT] = value;
            }

            return new BubbledInsertion(l, r, MIN_FANOUT, MIN_FANOUT, r[0]);
        }
    }

    private static class Internal {
        private Internal() {}

        private static int getKeyIndex(int index) {
            return index;
        }

        private static Object getKey(Object[] repr, int index) {
            return repr[getKeyIndex(index)];
        }

        private static int getNodeIndex(int index) {
            return MAX_FANOUT - 1 + index;
        }

        private static Object[] getNode(Object[] repr, int index) {
            return (Object[])repr[getNodeIndex(index)];
        }

        private static int[] getSizes(Object[] repr) {
            return (int[])repr[getNodeIndex(MAX_FANOUT)];
        }

        public static String toString(Object[] repr, int size) {
            final int[] sizes = getSizes(repr);

            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < -size; i++) {
                if (sb.length() != 0) {
                    sb.append(" |").append(getKey(repr, i - 1)).append("| ");
                }
                final Object[] node = getNode(repr, i);
                final int nodeSize = sizes[i];
                sb.append("{").append(isInternal(nodeSize) ? Internal.toString(node, nodeSize) : Leaf.toString(node, nodeSize)).append("}");
            }

            return sb.toString();
        }

        /** Always returns a valid index into the nodes array */
        public static int find(Object[] repr, int size, Object key, Comparator comparator) {
            assert size < 0;
            final int index = Arrays.binarySearch(repr, getKeyIndex(0), getKeyIndex(-size - 1), key, comparator);
            if (index < 0) {
                final int insertionPoint = -(index + 1);
                return insertionPoint;
            } else {
                return index + 1;
            }
        }

        public static boolean canPutAtIndex(int size) {
            assert size < 0;
            return -size < MAX_FANOUT;
        }

        public static void putAtIndex(Object[] repr, int[] sizeBox, int sizeIndex, int nodeIndex, BubbledInsertion toBubble) {
            assert sizeBox[sizeIndex] < 0;
            assert canPutAtIndex(sizeBox[sizeIndex]);

            // Tree:         Bubbled input:
            //  Key   0 1     Key    S
            //  Node 0 1 2    Node 1A 1B
            // If inserting at nodeIndex 1, shuffle things to:
            //  Key   0  S  1
            //  Node 0 1A 1B 2

            final int _size = -sizeBox[sizeIndex];
            final int[] sizes = getSizes(repr);
            System.arraycopy(repr,  getKeyIndex (nodeIndex),     repr,  getKeyIndex (nodeIndex + 1), _size - nodeIndex - 1);
            System.arraycopy(repr,  getNodeIndex(nodeIndex + 1), repr,  getNodeIndex(nodeIndex + 2), _size - nodeIndex - 1);
            System.arraycopy(sizes,              nodeIndex + 1,  sizes,              nodeIndex + 2,  _size - nodeIndex - 1);
            repr [getKeyIndex (nodeIndex)    ] = toBubble.separator;
            repr [getNodeIndex(nodeIndex)    ] = toBubble.leftObjects;
            sizes[             nodeIndex     ] = toBubble.leftSize;
            repr [getNodeIndex(nodeIndex + 1)] = toBubble.rightObjects;
            sizes[             nodeIndex + 1 ] = toBubble.rightSize;

            sizeBox[sizeIndex] = -(_size + 1);
        }

        public static BubbledInsertion bubblePutAtIndex(Object[] repr, int size, int nodeIndex, BubbledInsertion toBubble) {
            assert size < 0;
            assert !canPutAtIndex(size); // i.e. size == MAX_FANOUT

            final int[] sizes = getSizes(repr);

            // Tree:         Bubbled input:
            //  Key   0 1     Key    S
            //  Node 0 1 2    Node 1A  1B
            //
            // If inserting at nodeIndex 1, split things as:
            //
            // Separator: S
            // Left bubbled:  Right bubbled:
            //  Key   0        Key    1
            //  Node 0 1A      Node 1B 2

            final Object[] l = new Object[2 * MAX_FANOUT], r = new Object[2 * MAX_FANOUT];
            final int[] lSizes = new int[MAX_FANOUT];
            final int[] rSizes = new int[MAX_FANOUT];
            l[MAX_FANOUT * 2 - 1] = lSizes;
            r[MAX_FANOUT * 2 - 1] = rSizes;

            final Object separator;
            if (nodeIndex == MIN_FANOUT - 1) {
                separator = toBubble.separator;

                System.arraycopy(repr, getKeyIndex(0),              l, getKeyIndex(0), MIN_FANOUT - 1);
                System.arraycopy(repr, getKeyIndex(MIN_FANOUT - 1), r, getKeyIndex(0), MIN_FANOUT - 1);

                System.arraycopy(repr,  getNodeIndex(0),          l,      getNodeIndex(0), MIN_FANOUT - 1);
                System.arraycopy(sizes,              0,           lSizes,              0,  MIN_FANOUT - 1);
                System.arraycopy(repr,  getNodeIndex(MIN_FANOUT), r,      getNodeIndex(1), MIN_FANOUT - 1);
                System.arraycopy(sizes,              MIN_FANOUT,  rSizes,              1,  MIN_FANOUT - 1);

                l     [getNodeIndex(MIN_FANOUT - 1)] = toBubble.leftObjects;
                lSizes[             MIN_FANOUT - 1]  = toBubble.leftSize;
                r     [getNodeIndex(0)]              = toBubble.rightObjects;
                rSizes[             0]               = toBubble.rightSize;
            } else if (nodeIndex < MIN_FANOUT) {
                separator = getKey(repr, MIN_FANOUT - 2);

                System.arraycopy(repr, getKeyIndex(0),              l, getKeyIndex(0),             nodeIndex);
                System.arraycopy(repr, getKeyIndex(nodeIndex),      l, getKeyIndex(nodeIndex + 1), MIN_FANOUT - nodeIndex - 2);
                System.arraycopy(repr, getKeyIndex(MIN_FANOUT - 1), r, getKeyIndex(0),             MIN_FANOUT - 1);

                System.arraycopy(repr,  getNodeIndex(0),              l,      getNodeIndex(0),             nodeIndex);
                System.arraycopy(sizes,              0,               lSizes,              0,              nodeIndex);
                System.arraycopy(repr,  getNodeIndex(nodeIndex + 1),  l,      getNodeIndex(nodeIndex + 2), MIN_FANOUT - nodeIndex - 2);
                System.arraycopy(sizes,              nodeIndex + 1,   lSizes,              nodeIndex + 2,  MIN_FANOUT - nodeIndex - 2);
                System.arraycopy(repr,  getNodeIndex(MIN_FANOUT - 1), r,      getNodeIndex(0),             MIN_FANOUT);
                System.arraycopy(sizes,              MIN_FANOUT - 1,  rSizes,              0,              MIN_FANOUT);

                l[getKeyIndex(nodeIndex)] = toBubble.separator;
                l     [getNodeIndex(nodeIndex)]     = toBubble.leftObjects;
                lSizes[             nodeIndex]      = toBubble.leftSize;
                l     [getNodeIndex(nodeIndex + 1)] = toBubble.rightObjects;
                lSizes[             nodeIndex + 1]  = toBubble.rightSize;
            } else {
                nodeIndex -= MIN_FANOUT;
                // i.e. 0 <= nodeIndex < MIN_FANOUT - 1

                separator = getKey(repr, MIN_FANOUT - 1);

                System.arraycopy(repr, getKeyIndex(0),                      l, getKeyIndex(0),             MIN_FANOUT - 1);
                System.arraycopy(repr, getKeyIndex(MIN_FANOUT),             r, getKeyIndex(0),             nodeIndex);
                System.arraycopy(repr, getKeyIndex(MIN_FANOUT + nodeIndex), r, getKeyIndex(nodeIndex + 1), MIN_FANOUT - nodeIndex - 2);

                System.arraycopy(repr,  getNodeIndex(0),                          l,      getNodeIndex(0),             MIN_FANOUT);
                System.arraycopy(sizes,              0,                           lSizes,              0,              MIN_FANOUT);
                System.arraycopy(repr,  getNodeIndex(MIN_FANOUT),                 r,      getNodeIndex(0),             nodeIndex);
                System.arraycopy(sizes,              MIN_FANOUT,                  rSizes,              0,              nodeIndex);
                System.arraycopy(repr,  getNodeIndex(MIN_FANOUT + nodeIndex + 1), r,      getNodeIndex(nodeIndex + 2), MIN_FANOUT - nodeIndex - 2);
                System.arraycopy(sizes,              MIN_FANOUT + nodeIndex + 1,  rSizes,              nodeIndex + 2,  MIN_FANOUT - nodeIndex - 2);

                r[getKeyIndex(nodeIndex)] = toBubble.separator;
                r     [getNodeIndex(nodeIndex)]     = toBubble.leftObjects;
                rSizes[             nodeIndex]      = toBubble.leftSize;
                r     [getNodeIndex(nodeIndex + 1)] = toBubble.rightObjects;
                rSizes[             nodeIndex + 1]  = toBubble.rightSize;
            }

            return new BubbledInsertion(l, r, -MIN_FANOUT, -MIN_FANOUT, separator);
        }
    }

    private final Comparator<? super K> comparator;

    // Allocate these lazily to optimize allocation of lots of empty BTreeMaps
    private Object[] rootObjects;
    private int[] rootSizeBox;

    private int depth = 0; // Number of levels of internal nodes in the tree
    private int size = 0;

    private BTreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    public void check() {
        if (rootObjects == null) {
            assert rootSizeBox == null;
        } else {
            checkCore(rootObjects, rootSizeBox, 0, depth);
        }
    }

    private void checkCore(Object[] repr, int[] sizeBox, int sizeIndex, int depth) {
        final int size = sizeBox[sizeIndex];
        if (depth == 0) {
            for (int i = 0; i < size; i++) {
                assert Leaf.getKey(repr, i) != null;
            }
        } else {
            for (int i = 0; i < -size - 1; i++) {
                assert Internal.getKey(repr, i) != null;
            }

            final int[] sizes = Internal.getSizes(repr);
            for (int i = 0; i < -size; i++) {
                checkCore(Internal.getNode(repr, i), sizes, i, depth - 1);
            }
        }
    }

    @Override
    public Comparator<? super K> comparator() {
        return comparator;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> that) {
        if (that instanceof SortedMap && Objects.equals(this.comparator(), ((SortedMap)that).comparator())) {
            // TODO: fastpath?
        }

        for (Map.Entry<? extends K, ? extends V> e : that.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public V get(Object key) {
        return getOrDefault(key, null);
    }

    @Override
    public V getOrDefault(Object key, V dflt) {
        final Object[] resultBox = new Object[1];
        return getInternal(key, resultBox) ? (V)resultBox[0] : dflt;

    }

    @Override
    public boolean containsKey(Object key) {
        return getInternal(key, null);
    }

    private boolean getInternal(Object key, Object[] resultBox) {
        if (rootObjects == null) {
            return false;
        }

        Object[] nextObjects = rootObjects;
        int nextSize = rootSizeBox[0];
        int depth = this.depth;
        while (depth-- > 0) {
            final int ix = Internal.find(nextObjects, nextSize, key, comparator);
            nextSize    = Internal.getSizes(nextObjects)[ix];
            nextObjects = Internal.getNode(nextObjects, ix);
        }

        final int ix = Leaf.find(nextObjects, nextSize, key, comparator);
        if (ix < 0) {
            return false;
        } else {
            if (resultBox != null) {
                resultBox[0] = Leaf.getValue(nextObjects, ix);
            }
            return true;
        }
    }

    @Override
    public V put(K key, V value) {
        if (rootObjects == null) {
            rootObjects = new Object[2 * MAX_FANOUT];
            rootSizeBox = new int[1];
            final Object result = Leaf.putOrDieIfFull(rootObjects, rootSizeBox, 0, key, value, comparator);
            assert result == null;

            this.size = 1;
            return null;
        }

        final Object[] resultBox = new Object[1];
        final BubbledInsertion toBubble = putInternal(key, value, rootObjects, rootSizeBox, 0, this.depth, resultBox);
        if (toBubble == null) {
            return (V)resultBox[0];
        }

        final int[] sizes = new int[MAX_FANOUT];

        this.rootObjects = new Object[2 * MAX_FANOUT];
        this.rootSizeBox[0] = -2;
        this.rootObjects[MAX_FANOUT * 2 - 1] = sizes;
        this.rootObjects[Internal.getKeyIndex(0)] = toBubble.separator;
        this.rootObjects[Internal.getNodeIndex(0)] = toBubble.leftObjects;
        sizes           [                      0]  = toBubble.leftSize;
        this.rootObjects[Internal.getNodeIndex(1)] = toBubble.rightObjects;
        sizes           [                      1]  = toBubble.rightSize;

        this.depth++;
        return null;
    }

    @Override
    public V remove(Object key) {
        return null;
    }

    private BubbledInsertion putInternal(K key, V value, Object[] nextObjects, int[] nextSizeBox, int nextSizeIx, int depth, Object[] resultBox) {
        final int size = nextSizeBox[nextSizeIx];
        if (depth == 0) {
            final int nodeIndex = Leaf.find(nextObjects, size, key, comparator);
            if (nodeIndex < 0) this.size++;

            if (Leaf.canPutAtIndex(size, nodeIndex)) {
                resultBox[0] = Leaf.putAtIndex(nextObjects, nextSizeBox, nextSizeIx, nodeIndex, key, value);
                return null;
            }

            return Leaf.bubblePutAtIndex(nextObjects, size, nodeIndex, key, value);
        } else {
            final int nodeIndex = Internal.find(nextObjects, size, key, comparator);

            final BubbledInsertion toBubble = putInternal(key, value, Internal.getNode(nextObjects, nodeIndex), Internal.getSizes(nextObjects), nodeIndex, depth - 1, resultBox);
            if (toBubble == null) {
                return null;
            }

            if (Internal.canPutAtIndex(size)) {
                Internal.putAtIndex(nextObjects, nextSizeBox, nextSizeIx, nodeIndex, toBubble);
                return null;
            }

            return Internal.bubblePutAtIndex(nextObjects, size, nodeIndex, toBubble);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsValue(Object value) {
        return values().stream().anyMatch(v -> Objects.equals(v, value));
    }

    @Override
    public String toString() {
        // FIXME: replace with non-debugging printer
        return rootObjects == null ? "{}" : isInternal(rootSizeBox[0]) ? Internal.toString(rootObjects, rootSizeBox[0]) : Leaf.toString(rootObjects, rootSizeBox[0]);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> lowerEntry(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public K lowerKey(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> floorEntry(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public K floorKey(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> ceilingEntry(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public K ceilingKey(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> higherEntry(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public K higherKey(K key) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> firstEntry() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> lastEntry() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> pollFirstEntry() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Entry<K, V> pollLastEntry() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public NavigableMap<K, V> descendingMap() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public K firstKey() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public K lastKey() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException(); // FIXME
    }
}
