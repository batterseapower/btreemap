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
    private static final int MIN_FANOUT = 2;
    private static final int MAX_FANOUT = 2 * MIN_FANOUT - 1;

    private interface Node {}

    private static class Leaf implements Node {
        private final Object[] keysValues;
        private int size;

        public Leaf() {
            keysValues = new Object[MAX_FANOUT * 2];
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < size; i++) {
                if (sb.length() != 0) sb.append(", ");
                sb.append(keysValues[i]).append(": ").append(keysValues[MAX_FANOUT + i]);
            }

            return sb.toString();
        }

        public int find(Object key, Comparator comparator) {
            return Arrays.binarySearch(keysValues, 0, size, key, comparator);
        }

        public Object get(int index) {
            return keysValues[MAX_FANOUT + index];
        }

        public Object putOrDieIfFull(Object key, Object value, Comparator comparator) {
            int index = find(key, comparator);
            return putAtIndex(index, key, value);
        }

        public boolean canPutAtIndex(int index) {
            return index >= 0 || size < MAX_FANOUT;
        }

        /** @param index must be the index of key in the leaf, using same convention as Arrays.binarySearch */
        public Object putAtIndex(int index, Object key, Object value) {
            assert canPutAtIndex(index);

            final Object result;
            if (index < 0) {
                assert size < MAX_FANOUT;

                final int insertionPoint = -(index + 1);
                System.arraycopy(keysValues,              insertionPoint, keysValues,              insertionPoint + 1, size - insertionPoint);
                System.arraycopy(keysValues, MAX_FANOUT + insertionPoint, keysValues, MAX_FANOUT + insertionPoint + 1, size - insertionPoint);
                size++;

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
        public BubbledInsertion bubblePutAtIndex(int index, Object key, Object value) {
            assert !canPutAtIndex(index);
            assert size == MAX_FANOUT; // i.e. implies index < 0

            int insertionPoint = -(index + 1);
            final Leaf l = new Leaf(), r = new Leaf();
            l.size = r.size = MIN_FANOUT;

            if (insertionPoint < MIN_FANOUT) {
                copy(keysValues, 0,                           l.keysValues, 0,                  insertionPoint);
                copy(keysValues, insertionPoint,              l.keysValues, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);
                copy(keysValues, MIN_FANOUT - 1,              r.keysValues, 0,                  MIN_FANOUT);

                l.keysValues[insertionPoint]              = key;
                l.keysValues[insertionPoint + MAX_FANOUT] = value;
            } else {
                insertionPoint -= MIN_FANOUT;

                copy(keysValues, 0,                           l.keysValues, 0,                  MIN_FANOUT);
                copy(keysValues, MIN_FANOUT,                  r.keysValues, 0,                  insertionPoint);
                copy(keysValues, MIN_FANOUT + insertionPoint, r.keysValues, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);

                r.keysValues[insertionPoint]              = key;
                r.keysValues[insertionPoint + MAX_FANOUT] = value;
            }

            return new BubbledInsertion(l, r, r.keysValues[0]);
        }
    }

    private static class BubbledInsertion {
        private final Node left, right;
        private final Object separator; // The seperator key is <= all keys in the right and > all keys in the left

        private BubbledInsertion(Node left, Node right, Object separator) {
            this.left = left;
            this.right = right;
            this.separator = separator;
        }
    }

    private static class Internal implements Node {
        private final Object[] keys;
        private final Node[] nodes;
        private int size; // Number of nodes

        public Internal() {
            keys = new Object[MAX_FANOUT - 1];
            nodes = new Node[MAX_FANOUT];
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < size; i++) {
                if (sb.length() != 0) {
                    sb.append(" |").append(keys[i - 1]).append("| ");
                }
                sb.append("{").append(nodes[i]).append("}");
            }

            return sb.toString();
        }

        /** Always returns a valid index into the nodes array */
        public int find(Object key, Comparator comparator) {
            final int index = Arrays.binarySearch(keys, 0, size - 1, key, comparator);
            if (index < 0) {
                final int insertionPoint = -(index + 1);
                return insertionPoint;
            } else {
                return index + 1;
            }
        }

        public boolean canPutAtIndex() {
            return size < MAX_FANOUT;
        }

        public void putAtIndex(int nodeIndex, BubbledInsertion toBubble) {
            assert canPutAtIndex();

            // Tree:         Bubbled input:
            //  Key   0 1     Key    S
            //  Node 0 1 2    Node 1A 1B
            // If inserting at nodeIndex 1, shuffle things to:
            //  Key   0  S  1
            //  Node 0 1A 1B 2

            System.arraycopy(keys,  nodeIndex,     keys,  nodeIndex + 1, size - nodeIndex - 1);
            System.arraycopy(nodes, nodeIndex + 1, nodes, nodeIndex + 2, size - nodeIndex - 1);
            keys [nodeIndex    ] = toBubble.separator;
            nodes[nodeIndex    ] = toBubble.left;
            nodes[nodeIndex + 1] = toBubble.right;

            size++;
        }

        public BubbledInsertion bubblePutAtIndex(int nodeIndex, BubbledInsertion toBubble) {
            assert !canPutAtIndex(); // i.e. size == MAX_FANOUT

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

            final Internal l = new Internal(), r = new Internal();
            l.size = r.size = MIN_FANOUT;

            final Object separator;
            if (nodeIndex == MIN_FANOUT - 1) {
                separator = toBubble.separator;

                System.arraycopy(keys, 0,              l.keys, 0, MIN_FANOUT - 1);
                System.arraycopy(keys, MIN_FANOUT - 1, r.keys, 0, MIN_FANOUT - 1);

                System.arraycopy(nodes, 0,          l.nodes, 0, MIN_FANOUT - 1);
                System.arraycopy(nodes, MIN_FANOUT, r.nodes, 1, MIN_FANOUT - 1);

                l.nodes[MIN_FANOUT - 1] = toBubble.left;
                r.nodes[0]              = toBubble.right;
            } else if (nodeIndex < MIN_FANOUT) {
                separator = keys[MIN_FANOUT - 2];

                System.arraycopy(keys, 0,              l.keys, 0,             nodeIndex);
                System.arraycopy(keys, nodeIndex,      l.keys, nodeIndex + 1, MIN_FANOUT - nodeIndex - 2);
                System.arraycopy(keys, MIN_FANOUT - 1, r.keys, 0,             MIN_FANOUT - 1);

                System.arraycopy(nodes, 0,              l.nodes, 0,             nodeIndex);
                System.arraycopy(nodes, nodeIndex + 1,  l.nodes, nodeIndex + 2, MIN_FANOUT - nodeIndex - 2);
                System.arraycopy(nodes, MIN_FANOUT - 1, r.nodes, 0,             MIN_FANOUT);

                l.keys[nodeIndex] = toBubble.separator;
                l.nodes[nodeIndex]     = toBubble.left;
                l.nodes[nodeIndex + 1] = toBubble.right;
            } else {
                nodeIndex -= MIN_FANOUT;
                // i.e. 0 <= nodeIndex < MIN_FANOUT - 1

                separator = keys[MIN_FANOUT - 1];

                System.arraycopy(keys, 0,                      l.keys, 0,             MIN_FANOUT - 1);
                System.arraycopy(keys, MIN_FANOUT,             r.keys, 0,             nodeIndex);
                System.arraycopy(keys, MIN_FANOUT + nodeIndex, r.keys, nodeIndex + 1, MIN_FANOUT - nodeIndex - 2);

                System.arraycopy(nodes, 0,                          l.nodes, 0,             MIN_FANOUT);
                System.arraycopy(nodes, MIN_FANOUT,                 r.nodes, 0,             nodeIndex);
                System.arraycopy(nodes, MIN_FANOUT + nodeIndex + 1, r.nodes, nodeIndex + 2, MIN_FANOUT - nodeIndex - 2);

                r.keys[nodeIndex] = toBubble.separator;
                r.nodes[nodeIndex]     = toBubble.left;
                r.nodes[nodeIndex + 1] = toBubble.right;
            }

            return new BubbledInsertion(l, r, separator);
        }
    }

    private final Comparator<? super K> comparator;
    private Node root;
    private int depth = 0; // Number of levels of Internal nodes in the tree
    private int size = 0;

    private BTreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
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
    public void clear() {

    }

    @Override
    public V get(Object key) {
        return getOrDefault(key, null);
    }

    @Override
    public V getOrDefault(Object key, V dflt) {
        if (root == null) {
            return dflt;
        }

        Node next = root;
        int depth = this.depth;
        while (depth-- > 0) {
            final Internal node = (Internal)next;
            next = node.nodes[node.find(key, comparator)];
        }

        final Leaf node = (Leaf)next;
        final int ix = node.find(key, comparator);
        return ix < 0 ? dflt : (V)node.get(ix);
    }

    @Override
    public V put(K key, V value) {
        if (root == null) {
            final Leaf leaf = new Leaf();
            final Object result = leaf.putOrDieIfFull(key, value, comparator);
            assert result == null;

            root = leaf;
            return null;
        }

        final Object[] resultBox = new Object[1];
        final BubbledInsertion toBubble = putInternal(key, value, root, this.depth, resultBox);
        if (toBubble == null) {
            return (V)resultBox[0];
        }

        final Internal node = new Internal();
        node.size = 2;
        node.keys[0] = toBubble.separator;
        node.nodes[0] = toBubble.left;
        node.nodes[1] = toBubble.right;

        this.depth++;
        this.root = node;
        return null;
    }

    @Override
    public V remove(Object key) {
        return null;
    }

    private BubbledInsertion putInternal(K key, V value, Node next, int depth, Object[] resultBox) {
        if (depth == 0) {
            final Leaf node = (Leaf)next;
            final int nodeIndex = node.find(key, comparator);
            if (nodeIndex < 0) this.size++;

            if (node.canPutAtIndex(nodeIndex)) {
                resultBox[0] = node.putAtIndex(nodeIndex, key, value);
                return null;
            }

            return node.bubblePutAtIndex(nodeIndex, key, value);
        } else {
            final Internal node = (Internal)next;
            final int nodeIndex = node.find(key, comparator);

            final BubbledInsertion toBubble = putInternal(key, value, node.nodes[nodeIndex], depth - 1, resultBox);
            if (toBubble == null) {
                return null;
            }

            if (node.canPutAtIndex()) {
                node.putAtIndex(nodeIndex, toBubble);
                return null;
            }

            return node.bubblePutAtIndex(nodeIndex, toBubble);
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
        return root == null ? "{}" : root.toString();
    }

    @Override
    public boolean containsKey(Object key) {
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
