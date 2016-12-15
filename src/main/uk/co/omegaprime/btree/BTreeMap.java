package uk.co.omegaprime.btree;

import java.util.*;

import static uk.co.omegaprime.btree.Node.BINARY_SEARCH;
import static uk.co.omegaprime.btree.Node.MAX_FANOUT;
import static uk.co.omegaprime.btree.Node.MIN_FANOUT;

public class BTreeMap<K, V> implements NavigableMap<K, V>, NavigableMap2<K, V> {
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

    // Internal nodes and leaf nodes are both represented by an instance of the Node class. A Node is essentially
    // an Object[MAX_FANOUT * 2] with a size. For an internal node:
    //   - The first MAX_FANOUT - 1 elements of this will refer to keys
    //   - The next MAX_FANOUT elements will hold references to the Object[] of child nodes
    //   - The final element will hold a reference to a int[] of child node sizes
    //
    // For a leaf node:
    //   - The first MAX_FANOUT elements will refer to keys
    //   - The next MAX_FANOUT elements will refer to values
    //
    // Instead of Node, I used to just use a Object[] instead, with the size of all sibling nodes stored contiguously in another int[].
    // This was sort of fine but incurred more indirections & was fiddly to program against. Replacing it with this scheme
    // (such that the size and Objects are stored contiguously) sped up my get benchmark from 4.8M ops/sec to 5.3M ops/sec.
    //
    // FIXME: the single element at the end of each internal node is unused. What to do about this? Use for parent link?
    private static class BubbledInsertion {
        private final Node leftObjects, rightObjects;
        private final Object separator; // The seperator key is <= all keys in the right and > all keys in the left

        private BubbledInsertion(Node leftObjects, Node rightObjects, Object separator) {
            this.leftObjects = leftObjects;
            this.rightObjects = rightObjects;
            this.separator = separator;
        }
    }

    private static class Leaf {
        private Leaf() {}

        public static int find(Node keysValues, Object key, Comparator comparator) {
            final int size = keysValues.size;
            if (BINARY_SEARCH) {
                return keysValues.binarySearch(0, size, key, comparator);
            } else {
                for (int i = 0; i < size; i++) {
                    final Object checkKey = keysValues.get(i);
                    final int cmp = comparator == null ? ((Comparable)checkKey).compareTo(key) : comparator.compare(checkKey, key);
                    if (cmp == 0) {
                        return i;
                    } else if (cmp > 0) {
                        return -i - 1;
                    }
                }

                return -size - 1;
            }
        }

        public static Object getKey(Node keysValues, int index) {
            return keysValues.get(index);
        }

        public static Object getValue(Node keysValues, int index) {
            return keysValues.get(MAX_FANOUT + index);
        }

        public static Object putOrDieIfFull(Node keysValues, Object key, Object value, Comparator comparator) {
            int index = find(keysValues, key, comparator);
            return putAtIndex(keysValues, index, key, value);
        }

        public static boolean canPutAtIndex(int size, int index) {
            return index >= 0 || size < MAX_FANOUT;
        }

        /** @param index must be the index of key in the leaf, using same convention as Arrays.binarySearch */
        public static Object putAtIndex(Node keysValues, int index, Object key, Object value) {
            assert canPutAtIndex(keysValues.size, index);

            final Object result;
            if (index < 0) {
                final int size = keysValues.size;
                assert size < MAX_FANOUT;

                final int insertionPoint = -(index + 1);
                Node.arraycopy(keysValues,              insertionPoint, keysValues,              insertionPoint + 1, size - insertionPoint);
                Node.arraycopy(keysValues, MAX_FANOUT + insertionPoint, keysValues, MAX_FANOUT + insertionPoint + 1, size - insertionPoint);
                keysValues.size = size + 1;

                keysValues.set(insertionPoint, key);

                result = null;
                index = insertionPoint;
            } else {
                result = keysValues.get(MAX_FANOUT + index);
            }

            keysValues.set(MAX_FANOUT + index, value);
            return result;
        }

        private static void copy(Node srcKeysValues, int srcIndex, Node dstKeysValues, int dstIndex, int size) {
            Node.arraycopy(srcKeysValues, srcIndex,              dstKeysValues, dstIndex,              size);
            Node.arraycopy(srcKeysValues, srcIndex + MAX_FANOUT, dstKeysValues, dstIndex + MAX_FANOUT, size);
        }

        // This splits the leaf (of size MAX_FANOUT == 2 * MIN_FANOUT - 1) plus one extra item into two new
        // leaves, each of size MIN_FANOUT.
        public static BubbledInsertion bubblePutAtIndex(Node keysValues, int index, Object key, Object value) {
            assert !canPutAtIndex(keysValues.size, index);
            assert keysValues.size == MAX_FANOUT; // i.e. implies index < 0

            int insertionPoint = -(index + 1);
            final Node l = new Node(), r = new Node();
            l.size = r.size = MIN_FANOUT;

            if (insertionPoint < MIN_FANOUT) {
                copy(keysValues, 0,                           l, 0,                  insertionPoint);
                copy(keysValues, insertionPoint,              l, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);
                copy(keysValues, MIN_FANOUT - 1,              r, 0,                  MIN_FANOUT);

                l.set(insertionPoint,              key);
                l.set(insertionPoint + MAX_FANOUT, value);
            } else {
                insertionPoint -= MIN_FANOUT;

                copy(keysValues, 0,                           l, 0,                  MIN_FANOUT);
                copy(keysValues, MIN_FANOUT,                  r, 0,                  insertionPoint);
                copy(keysValues, MIN_FANOUT + insertionPoint, r, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);

                r.set(insertionPoint,              key);
                r.set(insertionPoint + MAX_FANOUT, value);
            }

            return new BubbledInsertion(l, r, r.get(0));
        }
    }

    private static class Internal {
        private Internal() {}

        private static int getKeyIndex(int index) {
            return index;
        }

        private static Object getKey(Node repr, int index) {
            return repr.get(getKeyIndex(index));
        }

        private static int getNodeIndex(int index) {
            return MAX_FANOUT - 1 + index;
        }

        private static Node getNode(Node repr, int index) {
            return (Node)repr.get(getNodeIndex(index));
        }

        /** Always returns a valid index into the nodes array. Keys in the node indicated in the index will be >= key */
        public static int find(Node repr, Object key, Comparator comparator) {
            final int size = repr.size;
            if (BINARY_SEARCH) {
                final int index = repr.binarySearch(0, size - 1, key, comparator);
                return index < 0 ? -(index + 1) : index + 1;
            } else {
                // Tried doing the comparator == null check outside the loop, but not a significant speed boost
                for (int i = 0; i < size - 1; i++) {
                    final Object checkKey = repr.get(i);
                    final int cmp = comparator == null ? ((Comparable)checkKey).compareTo(key) : comparator.compare(checkKey, key);
                    if (cmp > 0) {
                        return i;
                    }
                }

                return size - 1;
            }
        }

        public static boolean canPutAtIndex(int size) {
            return size < MAX_FANOUT;
        }

        public static void putAtIndex(Node repr, int nodeIndex, BubbledInsertion toBubble) {
            assert canPutAtIndex(repr.size);

            // Tree:         Bubbled input:
            //  Key   0 1     Key    S
            //  Node 0 1 2    Node 1A 1B
            // If inserting at nodeIndex 1, shuffle things to:
            //  Key   0  S  1
            //  Node 0 1A 1B 2

            final int size = repr.size;
            Node.arraycopy(repr,  getKeyIndex (nodeIndex),     repr,  getKeyIndex (nodeIndex + 1), size - nodeIndex - 1);
            Node.arraycopy(repr,  getNodeIndex(nodeIndex + 1), repr,  getNodeIndex(nodeIndex + 2), size - nodeIndex - 1);
            repr.set(getKeyIndex (nodeIndex)    , toBubble.separator);
            repr.set(getNodeIndex(nodeIndex)    , toBubble.leftObjects);
            repr.set(getNodeIndex(nodeIndex + 1), toBubble.rightObjects);

            repr.size = size + 1;
        }

        public static BubbledInsertion bubblePutAtIndex(Node repr, int nodeIndex, BubbledInsertion toBubble) {
            assert !canPutAtIndex(repr.size); // i.e. size == MAX_FANOUT

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

            final Node l = new Node(), r = new Node();
            l.size = r.size = MIN_FANOUT;

            final Object separator;
            if (nodeIndex == MIN_FANOUT - 1) {
                separator = toBubble.separator;

                Node.arraycopy(repr, getKeyIndex(0),              l, getKeyIndex(0), MIN_FANOUT - 1);
                Node.arraycopy(repr, getKeyIndex(MIN_FANOUT - 1), r, getKeyIndex(0), MIN_FANOUT - 1);

                Node.arraycopy(repr,  getNodeIndex(0),          l,      getNodeIndex(0), MIN_FANOUT - 1);
                Node.arraycopy(repr,  getNodeIndex(MIN_FANOUT), r,      getNodeIndex(1), MIN_FANOUT - 1);

                l.set(getNodeIndex(MIN_FANOUT - 1), toBubble.leftObjects);
                r.set(getNodeIndex(0),              toBubble.rightObjects);
            } else if (nodeIndex < MIN_FANOUT) {
                separator = getKey(repr, MIN_FANOUT - 2);

                Node.arraycopy(repr, getKeyIndex(0),              l, getKeyIndex(0),             nodeIndex);
                Node.arraycopy(repr, getKeyIndex(nodeIndex),      l, getKeyIndex(nodeIndex + 1), MIN_FANOUT - nodeIndex - 2);
                Node.arraycopy(repr, getKeyIndex(MIN_FANOUT - 1), r, getKeyIndex(0),             MIN_FANOUT - 1);

                Node.arraycopy(repr,  getNodeIndex(0),              l,      getNodeIndex(0),             nodeIndex);
                Node.arraycopy(repr,  getNodeIndex(nodeIndex + 1),  l,      getNodeIndex(nodeIndex + 2), MIN_FANOUT - nodeIndex - 2);
                Node.arraycopy(repr,  getNodeIndex(MIN_FANOUT - 1), r,      getNodeIndex(0),             MIN_FANOUT);

                l.set(getKeyIndex(nodeIndex), toBubble.separator);
                l.set(getNodeIndex(nodeIndex),     toBubble.leftObjects);
                l.set(getNodeIndex(nodeIndex + 1), toBubble.rightObjects);
            } else {
                nodeIndex -= MIN_FANOUT;
                // i.e. 0 <= nodeIndex < MIN_FANOUT - 1

                separator = getKey(repr, MIN_FANOUT - 1);

                Node.arraycopy(repr, getKeyIndex(0),                      l, getKeyIndex(0),             MIN_FANOUT - 1);
                Node.arraycopy(repr, getKeyIndex(MIN_FANOUT),             r, getKeyIndex(0),             nodeIndex);
                Node.arraycopy(repr, getKeyIndex(MIN_FANOUT + nodeIndex), r, getKeyIndex(nodeIndex + 1), MIN_FANOUT - nodeIndex - 2);

                Node.arraycopy(repr,  getNodeIndex(0),                          l,      getNodeIndex(0),             MIN_FANOUT);
                Node.arraycopy(repr,  getNodeIndex(MIN_FANOUT),                 r,      getNodeIndex(0),             nodeIndex);
                Node.arraycopy(repr,  getNodeIndex(MIN_FANOUT + nodeIndex + 1), r,      getNodeIndex(nodeIndex + 2), MIN_FANOUT - nodeIndex - 2);

                r.set(getKeyIndex(nodeIndex), toBubble.separator);
                r.set(getNodeIndex(nodeIndex),     toBubble.leftObjects);
                r.set(getNodeIndex(nodeIndex + 1), toBubble.rightObjects);
            }

            return new BubbledInsertion(l, r, separator);
        }
    }

    private final Comparator<? super K> comparator;

    // Allocate these lazily to optimize allocation of lots of empty BTreeMaps
    private Node rootObjects;

    private int depth; // Number of levels of internal nodes in the tree
    private int size;

    private BTreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    public void check() {
        if (rootObjects != null) {
            checkCore(rootObjects, depth);
        }
    }

    private void checkCore(Node repr, int depth) {
        final int size = repr.size;
        if (depth == 0) {
            for (int i = 0; i < size; i++) {
                assert Leaf.getKey(repr, i) != null;
            }
        } else {
            for (int i = 0; i < size - 1; i++) {
                assert Internal.getKey(repr, i) != null;
            }

            for (int i = 0; i < size; i++) {
                checkCore(Internal.getNode(repr, i), depth - 1);
            }
        }
    }

    @Override
    public void clear() {
        rootObjects = null;
        depth = 0;
        size = 0;
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
        if (rootObjects == null) {
            return dflt;
        }

        Node nextObjects = rootObjects;
        int depth = this.depth;
        while (depth-- > 0) {
            final int ix = Internal.find(nextObjects, key, comparator);
            nextObjects = Internal.getNode(nextObjects, ix);
        }

        final int ix = Leaf.find(nextObjects, key, comparator);
        if (ix < 0) {
            return dflt;
        } else {
            return (V)Leaf.getValue(nextObjects, ix);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        if (rootObjects == null) {
            return false;
        }

        Node nextObjects = rootObjects;
        int depth = this.depth;
        while (depth-- > 0) {
            final int ix = Internal.find(nextObjects, key, comparator);
            nextObjects = Internal.getNode(nextObjects, ix);
        }

        final int ix = Leaf.find(nextObjects, key, comparator);
        return ix >= 0;
    }

    @Override
    public V put(K key, V value) {
        if (rootObjects == null) {
            rootObjects = new Node();
            final Object result = Leaf.putOrDieIfFull(rootObjects, key, value, comparator);
            assert result == null;

            this.size = 1;
            return null;
        }

        final Object[] resultBox = new Object[1];
        final BubbledInsertion toBubble = putInternal(key, value, rootObjects, this.depth, resultBox);
        if (toBubble == null) {
            return (V)resultBox[0];
        }

        this.rootObjects = new Node();
        this.rootObjects.size = 2;
        this.rootObjects.set(Internal.getKeyIndex (0), toBubble.separator);
        this.rootObjects.set(Internal.getNodeIndex(0), toBubble.leftObjects);
        this.rootObjects.set(Internal.getNodeIndex(1), toBubble.rightObjects);

        this.depth++;
        return null;
    }

    private BubbledInsertion putInternal(K key, V value, Node nextObjects, int depth, Object[] resultBox) {
        final int size = nextObjects.size;
        if (depth == 0) {
            final int nodeIndex = Leaf.find(nextObjects, key, comparator);
            if (nodeIndex < 0) this.size++;

            if (Leaf.canPutAtIndex(size, nodeIndex)) {
                resultBox[0] = Leaf.putAtIndex(nextObjects, nodeIndex, key, value);
                return null;
            }

            return Leaf.bubblePutAtIndex(nextObjects, nodeIndex, key, value);
        } else {
            final int nodeIndex = Internal.find(nextObjects, key, comparator);

            final BubbledInsertion toBubble = putInternal(key, value, Internal.getNode(nextObjects, nodeIndex), depth - 1, resultBox);
            if (toBubble == null) {
                return null;
            }

            if (Internal.canPutAtIndex(size)) {
                Internal.putAtIndex(nextObjects, nodeIndex, toBubble);
                return null;
            }

            return Internal.bubblePutAtIndex(nextObjects, nodeIndex, toBubble);
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

    public boolean containsValue(Object value) {
        return values().stream().anyMatch(v -> Objects.equals(v, value));
    }

    @Override
    public String toString() {
        if (false) {
            return rootObjects == null ? "{}" : toStringInternal(rootObjects, depth);
        } else {
            return Iterables.toMapString(this.entrySet());
        }
    }

    @Override
    public boolean equals(Object that) {
        return SortedMaps.equals(this, that);
    }

    @Override
    public int hashCode() {
        return Iterables.hashCode(entrySet());
    }

    private static String toStringInternal(Node repr, int depth) {
        if (depth == 0) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < repr.size; i++) {
                if (sb.length() != 0) sb.append(", ");
                sb.append(Leaf.getKey(repr, i)).append(": ").append(Leaf.getValue(repr, i));
            }

            return sb.toString();
        } else {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < repr.size; i++) {
                if (sb.length() != 0) {
                    sb.append(" |").append(Internal.getKey(repr, i - 1)).append("| ");
                }
                final Node node = Internal.getNode(repr, i);
                sb.append("{").append(toStringInternal(node, depth - 1)).append("}");
            }

            return sb.toString();
        }
    }

    static <K, V> K getEntryKey(Entry<K, V> e) {
        return e == null ? null : e.getKey();
    }

    @Override
    public Entry<K, V> lowerEntry(K key) {
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node backtrackParent = null; // Deepest internal node on the path to "key" which has a child prior to the one we descended into
        int backtrackIndex = -1;     // Index of that prior child
        int backtrackDepth = -1;     // Depth of that internal node

        Node repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final int index = Internal.find(repr, key, comparator);
            if (index > 0) {
                backtrackParent = repr;
                backtrackIndex = index - 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(repr, index);
        }

        final int leafIndex = Leaf.find(repr, key, comparator);
        final int insertionPoint = leafIndex >= 0 ? leafIndex : -(leafIndex + 1);
        final int returnIndex;
        if (insertionPoint > 0) {
            returnIndex = insertionPoint - 1;
        } else {
            // insertionPoint == 0: we need to find the last item in the prior leaf node.
            if (backtrackParent == null) {
                // Oh -- that was the first leaf node
                return null;
            }

            repr = backtrackParent;
            int index = backtrackIndex;
            for (int i = backtrackDepth; i < depth; i++) {
                repr = Internal.getNode(repr, index);
                index = repr.size - 1;
            }

            returnIndex = index;
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            (K)Leaf.getKey  (repr, returnIndex),
            (V)Leaf.getValue(repr, returnIndex)
        );
    }

    @Override
    public K lowerKey(K key) {
        return getEntryKey(lowerEntry(key));
    }

    @Override
    public Entry<K, V> floorEntry(K key) {
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node backtrackParent = null; // Deepest internal node on the path to "key" which has a child prior to the one we descended into
        int backtrackIndex = -1;     // Index of that prior child
        int backtrackDepth = -1;     // Depth of that internal node

        Node repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final int index = Internal.find(repr, key, comparator);
            if (index > 0) {
                backtrackParent = repr;
                backtrackIndex = index - 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(repr, index);
        }

        final int leafIndex = Leaf.find(repr, key, comparator);
        final int returnIndex;
        if (leafIndex >= 0) {
            returnIndex = leafIndex;
        } else {
            final int insertionPoint = -(leafIndex + 1);
            if (insertionPoint > 0) {
                returnIndex = insertionPoint - 1;
            } else {
                // insertionPoint == 0: we need to find the last item in the prior leaf node.
                if (backtrackParent == null) {
                    // Oh -- that was the first leaf node
                    return null;
                }

                repr = backtrackParent;
                int index = backtrackIndex;
                for (int i = backtrackDepth; i < depth; i++) {
                    repr = Internal.getNode(repr, index);
                    index = repr.size - 1;
                }

                returnIndex = index;
            }
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            (K)Leaf.getKey  (repr, returnIndex),
            (V)Leaf.getValue(repr, returnIndex)
        );
    }

    @Override
    public K floorKey(K key) {
        return getEntryKey(floorEntry(key));
    }

    @Override
    public Entry<K, V> ceilingEntry(K key) {
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node backtrackParent = null; // Deepest internal node on the path to "key" which has a child next to the one we descended into
        int backtrackIndex = -1;     // Index of that next child
        int backtrackDepth = -1;     // Depth of that internal node

        Node repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final int index = Internal.find(repr, key, comparator);
            if (index < repr.size - 1) {
                backtrackParent = repr;
                backtrackIndex = index + 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(repr, index);
        }

        final int leafIndex = Leaf.find(repr, key, comparator);
        final int returnIndex;
        if (leafIndex >= 0) {
            returnIndex = leafIndex;
        } else {
            final int insertionPoint = -(leafIndex + 1);
            if (insertionPoint < repr.size) {
                returnIndex = insertionPoint;
            } else {
                // insertionPoint == repr.size: we need to find the first item in the next leaf node.
                if (backtrackParent == null) {
                    // Oh -- that was the last leaf node
                    return null;
                }

                repr = backtrackParent;
                int index = backtrackIndex;
                for (int i = backtrackDepth; i < depth; i++) {
                    repr = Internal.getNode(repr, index);
                    index = 0;
                }

                returnIndex = index;
            }
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            (K)Leaf.getKey  (repr, returnIndex),
            (V)Leaf.getValue(repr, returnIndex)
        );
    }

    @Override
    public K ceilingKey(K key) {
        return getEntryKey(ceilingEntry(key));
    }

    @Override
    public Entry<K, V> higherEntry(K key) {
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node backtrackParent = null; // Deepest internal node on the path to "key" which has a child next to the one we descended into
        int backtrackIndex = -1;     // Index of that next child
        int backtrackDepth = -1;     // Depth of that internal node

        Node repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final int index = Internal.find(repr, key, comparator);
            if (index < repr.size - 1) {
                backtrackParent = repr;
                backtrackIndex = index + 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(repr, index);
        }

        final int leafIndex = Leaf.find(repr, key, comparator);
        final int insertionPoint = leafIndex >= 0 ? leafIndex + 1 : -(leafIndex + 1);
        final int returnIndex;
        if (insertionPoint < repr.size) {
            returnIndex = insertionPoint;
        } else {
            // insertionPoint == repr.size: we need to find the first item in the next leaf node.
            if (backtrackParent == null) {
                // Oh -- that was the last leaf node
                return null;
            }

            repr = backtrackParent;
            int index = backtrackIndex;
            for (int i = backtrackDepth; i < depth; i++) {
                repr = Internal.getNode(repr, index);
                index = 0;
            }

            returnIndex = index;
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            (K)Leaf.getKey  (repr, returnIndex),
            (V)Leaf.getValue(repr, returnIndex)
        );
    }

    @Override
    public K higherKey(K key) {
        return getEntryKey(higherEntry(key));
    }

    @Override
    public Entry<K, V> firstEntry() {
        if (rootObjects == null) {
            return null;
        }

        Node repr = rootObjects;
        int depth = this.depth;

        while (depth-- > 0) {
            final int index = 0;
            repr = Internal.getNode(repr, index);
        }

        final int size = repr.size;
        if (size == 0) {
            return null;
        } else {
            final int index = 0;
            return new AbstractMap.SimpleImmutableEntry<>(
                (K)Leaf.getKey  (repr, index),
                (V)Leaf.getValue(repr, index)
            );
        }
    }

    @Override
    public Entry<K, V> lastEntry() {
        if (rootObjects == null) {
            return null;
        }

        Node repr = rootObjects;
        int depth = this.depth;

        while (depth-- > 0) {
            final int index = repr.size - 1;
            repr = Internal.getNode(repr, index);
        }

        final int size = repr.size;
        if (size == 0) {
            return null;
        } else {
            final int index = size - 1;
            return new AbstractMap.SimpleImmutableEntry<>(
                (K)Leaf.getKey  (repr, index),
                (V)Leaf.getValue(repr, index)
            );
        }
    }

    @Override
    public Entry<K, V> pollFirstEntry() {
        // TODO: fast path?
        final Entry<K, V> e = firstEntry();
        if (e != null) {
            remove(e.getKey());
        }

        return e;
    }

    @Override
    public Entry<K, V> pollLastEntry() {
        // TODO: fast path?
        final Entry<K, V> e = lastEntry();
        if (e != null) {
            remove(e.getKey());
        }

        return e;
    }

    @Override
    public NavigableMap<K, V> descendingMap() {
        return new DescendingNavigableMap<K, V>(this);
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        return new NavigableMapKeySet<K>(this);
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    @Override
    public NavigableMap2<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return new RestrictedNavigableMap<>(
                this, fromKey, toKey,
                RestrictedNavigableMap.Bound.inclusive(fromInclusive),
                RestrictedNavigableMap.Bound.inclusive(toInclusive));
    }

    @Override
    public NavigableMap2<K, V> headMap(K toKey, boolean inclusive) {
        return new RestrictedNavigableMap<>(
                this, null, toKey,
                RestrictedNavigableMap.Bound.MISSING,
                RestrictedNavigableMap.Bound.inclusive(inclusive));
    }

    @Override
    public NavigableMap2<K, V> tailMap(K fromKey, boolean inclusive) {
        return new RestrictedNavigableMap<>(
                this, fromKey, null,
                RestrictedNavigableMap.Bound.inclusive(inclusive),
                RestrictedNavigableMap.Bound.MISSING);
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
        return headMap(toKey, false);
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
        return tailMap(fromKey, true);
    }

    @Override
    public K firstKey() {
        final Entry<K, V> e = firstEntry();
        if (e == null) throw new NoSuchElementException();

        return e.getKey();
    }

    @Override
    public K lastKey() {
        final Entry<K, V> e = lastEntry();
        if (e == null) throw new NoSuchElementException();

        return e.getKey();
    }

    @Override
    public NavigableSet<K> keySet() {
        return navigableKeySet();
    }

    @Override
    public Collection<V> values() {
        return new MapValueCollection<>(this);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new MapEntrySet<>(this, () -> new Iterator<Entry<K, V>>() {
            // indexes[0] is an index into rootObjects.
            // indexes[i] is an index into nodes[i - 1] (for i >= 1)
            private final int[] indexes = new int[depth + 1];
            private final Node[] nodes = new Node[depth];
            // If nextLevel >= 0:
            //   1. indexes[nextLevel] < size - 1
            //   2. There is no level l > nextLevel such that indexes[l] < size - 1
            private int nextLevel = -1;
            private boolean hasNext = false;

            {
                if (rootObjects != null) {
                    Node node = rootObjects;
                    for (int i = 0;; i++) {
                        final int index = indexes[i] = 0;
                        if (index < node.size - 1) {
                            nextLevel = i;
                        }

                        if (i >= nodes.length) {
                            break;
                        }

                        node = nodes[i] = Internal.getNode(node, index);
                    }

                    hasNext = node.size > 0;
                }
            }

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public Entry<K, V> next() {
                if (!hasNext) {
                    throw new NoSuchElementException();
                }

                final Entry<K, V> result;
                {
                    final Node leafNode = nodes.length == 0 ? rootObjects : nodes[nodes.length - 1];
                    final int ix = indexes[indexes.length - 1];
                    result = new AbstractMap.SimpleImmutableEntry<K, V>(
                            (K)Leaf.getKey(leafNode, ix),
                            (V)Leaf.getValue(leafNode, ix)
                    );
                }

                if (nextLevel < 0) {
                    hasNext = false;
                } else {
                    int index = ++indexes[nextLevel];
                    Node node = nextLevel == 0 ? rootObjects : nodes[nextLevel - 1];
                    assert index < node.size;
                    if (nextLevel < nodes.length) {
                        // We stepped forward to a later item in an internal node: update all children
                        for (int i = nextLevel; i < nodes.length;) {
                            node = nodes[i++] = Internal.getNode(node, index);
                            index = indexes[i] = 0;
                        }

                        nextLevel = nodes.length;
                    } else if (index == node.size - 1) {
                        // We stepped forward to the last item in a leaf node: find parent we should step forward next
                        assert nextLevel == nodes.length;
                        nextLevel = -1;
                        for (int i = nodes.length - 1; i >= 0; i--) {
                            node = i == 0 ? rootObjects : nodes[i - 1];
                            index = indexes[i];
                            if (index < node.size - 1) {
                                nextLevel = i;
                                break;
                            }
                        }
                    }
                }

                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException(); // FIXME
            }
        });
    }

    @Override
    public Set<Entry<K, V>> descendingEntrySet() {
        return new MapEntrySet<>(this, () -> new Iterator<Entry<K, V>>() {
            // indexes[0] is an index into rootObjects.
            // indexes[i] is an index into nodes[i - 1] (for i >= 1)
            private final int[] indexes = new int[depth + 1];
            private final Node[] nodes = new Node[depth];
            // If nextLevel >= 0:
            //   1. indexes[nextLevel] > 0
            //   2. There is no level l > nextLevel such that indexes[l] > 0
            private int nextLevel = -1;
            private boolean hasNext = false;

            {
                if (rootObjects != null) {
                    Node node = rootObjects;
                    for (int i = 0;; i++) {
                        final int index = indexes[i] = node.size - 1;
                        if (index > 0) {
                            nextLevel = i;
                        }

                        if (i >= nodes.length) {
                            break;
                        }

                        node = nodes[i] = Internal.getNode(node, index);
                    }

                    hasNext = node.size > 0;
                }
            }

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public Entry<K, V> next() {
                if (!hasNext) {
                    throw new NoSuchElementException();
                }

                final Entry<K, V> result;
                {
                    final Node leafNode = nodes.length == 0 ? rootObjects : nodes[nodes.length - 1];
                    final int ix = indexes[indexes.length - 1];
                    result = new AbstractMap.SimpleImmutableEntry<K, V>(
                        (K)Leaf.getKey(leafNode, ix),
                        (V)Leaf.getValue(leafNode, ix)
                    );
                }

                if (nextLevel < 0) {
                    hasNext = false;
                } else {
                    int index = --indexes[nextLevel];
                    assert index >= 0;
                    if (nextLevel < nodes.length) {
                        // We stepped back to an earlier item in an internal node: update all children
                        Node node = nextLevel == 0 ? rootObjects : nodes[nextLevel - 1];
                        for (int i = nextLevel; i < nodes.length;) {
                            node = nodes[i++] = Internal.getNode(node, index);
                            index = indexes[i] = node.size - 1;
                            assert index > 0;
                        }

                        nextLevel = nodes.length;
                    } else if (index == 0) {
                        // We stepped back to the first item in a leaf node: find parent we should step back next
                        assert nextLevel == nodes.length;
                        nextLevel = -1;
                        for (int i = nodes.length - 1; i >= 0; i--) {
                            //Node node = i == 0 ? rootObjects : nodes[i - 1];
                            index = indexes[i];
                            if (index > 0) {
                                nextLevel = i;
                                break;
                            }
                        }
                    }
                }

                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException(); // FIXME
            }
        });
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException(); // FIXME
    }
}
