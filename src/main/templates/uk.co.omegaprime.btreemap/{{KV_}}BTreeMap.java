package uk.co.omegaprime.btreemap;

import java.util.*;

import static uk.co.omegaprime.btreemap.Node.BINARY_SEARCH;
import static uk.co.omegaprime.btreemap.Node.MAX_FANOUT;
import static uk.co.omegaprime.btreemap.Node.MIN_FANOUT;

/**
 * A B-tree based {@link NavigableMap} implementation for {{K.erased}} keys and {{V.erased}} values.
 * <p>
 * To get started, call {@link #create()}.
 * <p>
 * All values in the map are stored at leaf nodes, and all leaf nodes are at the same depth. This ensures that accesses
 * to the map via e.g. {@code get}, {@code put} and {@code remove} all take log(n) time. In contrast to a balanced binary
 * tree (like that used by {@link java.util.TreeMap}), each node may holds more than one key. This makes the datastructure
 * more cache-friendly: you can expect it to be around 50% faster than {@code TreeMap} for workloads that do not fit in cache.
 * <p>
 * The map is sorted either according to the Comparable method of the key type, or via a user-supplied {@code Comparator}.
 * This ordering should be consistent with {@code equals}.
 * <p>
 * The implementation is unsynchronized, and there are no guarantees as to what will happen if you make use of iterator
 * that was created before some operation that modified the map.
 * <p>
 * {@code Entry} instances returned by this class are immutable and hence do not support the {@link Entry#setValue(Object)} method.
 */
public class BTreeMap<$K$, $V$> implements NavigableMap<@Boxed $K$, @Boxed $V$> {
    /** Create as empty {@code BTreeMap} that uses the natural order of the keys */
    public static <$K$ extends Comparable<? super $K$>, $V$> BTreeMap<$K$, $V$> create() {
        return new BTreeMap<$K$, $V$>(null);
    }

    /**
     * Create an empty {@code BTreeMap} that uses a custom comparator on the keys.
     *
     * @param comparator If null, the natural order of the keys will be used
     */
    public static <$K$, $V$> BTreeMap<$K$, $V$> create(Comparator/* wait for it.. */<? super @Boxed $K$> comparator) {
        return new BTreeMap<$K$, $V$>({% if K.isObject() %}comparator{% else %}{{K_}}Comparator.unbox(comparator){% endif %});
    }

    {% if K.isPrimitive %}
    /**
     * Create an empty {@code BTreeMap} that uses a custom comparator on the keys.
     *
     * @param comparator If null, the natural order of the keys will be used
     */
    public static <$K$, $V$> BTreeMap<$K$, $V$> create(Comparator<$K$> comparator) {
        return new BTreeMap<$K$, $V$>(comparator);
    }
    {% endif %}

    /** Create a new map that contains the same entries as the specified {@code SortedMap}, and also shares a key ordering with it */
    public static <$K$, $V$> BTreeMap<$K$, $V$> create(SortedMap<@Boxed $K$, ? extends @Boxed $V$> that) {
        final BTreeMap<$K$, $V$> result = create(that.comparator());
        result.putAll(that);
        return result;
    }

    /** Create a new map that contains the same entries as the specified {@code Map}, and uses the natural ordering on the keys */
    public static <$K$, $V$> BTreeMap<$K$, $V$> create(Map<? extends @Boxed $K$, ? extends @Boxed $V$> that) {
        final BTreeMap<$K$, $V$> result = new BTreeMap<$K$, $V$>(null);
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
    // FIXME: the single element at the end of each internal node is unused. What to do about this? Use for parent link -- might help us avoid allocation in iterator?
    private static class BubbledInsertion<$K$> {
        private final AbstractNode leftObjects, rightObjects;
        private final $K$ separator; // The seperator key is <= all keys in the right and > all keys in the left

        private {{K_}}BubbledInsertion(AbstractNode leftObjects, AbstractNode rightObjects, $K$ separator) {
            this.leftObjects = leftObjects;
            this.rightObjects = rightObjects;
            this.separator = separator;
        }
    }

    private static class Leaf {
        private Leaf() {}

        public static <$K$, $V$> int find(Node<$K$, $V$> repr, @Erased $K$ key, {{K_}}Comparator comparator) {
            final int size = repr.size;
            if (BINARY_SEARCH) {
                return repr.binarySearch(0, size, key, comparator);
            } else if (comparator == null) {
                // Tried not exiting early (improves branch prediction) but significantly worse.
                int i;
                for (i = 0; i < size; i++) {
                    final $K$ checkKey = repr.getKey(i);
                    {% if K.isPrimitive %}
                    // It's very much worth avoiding casting a primitive to Comparable (50% speedup)
                    if (checkKey == key) {
                        return i;
                    } else if (checkKey > key) {
                        return -i - 1;
                    }
                    {% else %}
                    final int cmp = ((Comparable)checkKey).compareTo(key);
                    if (cmp == 0) {
                        return i;
                    } else if (cmp > 0) {
                        return -i - 1;
                    }
                    {% endif %}
                }

                return -size - 1;
            } else {
                // Tried not exiting early (improves branch prediction) but significantly worse.
                int i;
                for (i = 0; i < size; i++) {
                    final $K$ checkKey = repr.getKey(i);
                    final int cmp = comparator.compare{% if K.isPrimitive %}{{K.name}}{% endif %}(checkKey, key);
                    if (cmp == 0) {
                        return i;
                    } else if (cmp > 0) {
                        return -i - 1;
                    }
                }

                return -size - 1;
            }
        }

        public static <$K$, $V$> $K$ getKey(Node<$K$, $V$> keysValues, int index) {
            return keysValues.getKey(index);
        }

        public static <$K$, $V$> $V$ getValue(Node<$K$, $V$> keysValues, int index) {
            return keysValues.getValue(index);
        }

        public static boolean canPutAtIndex(int size, int index) {
            return index >= 0 || size < MAX_FANOUT;
        }

        /** @param index must be the index of existing key in the leaf */
        public static <$K$, $V$> $V$ putOverwriteIndex(Node<$K$, $V$> keysValues, int index, $K$ key, $V$ value) {
            assert index >= 0;

            final $V$ result = keysValues.getValue(index);
            keysValues.setValue(index, value);
            return result;
        }

        /** @param index must be the insertion point in the leaf, using same convention as Arrays.binarySearch */
        public static <$K$, $V$> void putInsertIndex(Node<$K$, $V$> keysValues, int index, $K$ key, $V$ value) {
            assert index < 0 && keysValues.size < MAX_FANOUT;

            final int size = keysValues.size;
            final int insertionPoint = -(index + 1);
            assert size < MAX_FANOUT && insertionPoint <= size;

            {{KV_}}Node.arraycopyKey  (keysValues, insertionPoint, keysValues, insertionPoint + 1, size - insertionPoint);
            {{KV_}}Node.arraycopyValue(keysValues, insertionPoint, keysValues, insertionPoint + 1, size - insertionPoint);
            keysValues.size = size + 1;

            keysValues.setKey(insertionPoint, key);
            keysValues.setValue(insertionPoint, value);
        }

        private static <$K$, $V$> void copy(Node<$K$, $V$> srcKeysValues, int srcIndex, Node<? super $K$, ? super $V$> dstKeysValues, int dstIndex, int size) {
            {{KV_}}Node.arraycopyKey  (srcKeysValues, srcIndex, dstKeysValues, dstIndex, size);
            {{KV_}}Node.arraycopyValue(srcKeysValues, srcIndex, dstKeysValues, dstIndex, size);
        }

        // This splits the leaf (of size MAX_FANOUT == 2 * MIN_FANOUT - 1) plus one extra item into two new
        // leaves, each of size MIN_FANOUT.
        public static <$K$, $V$> BubbledInsertion<$K$> bubblePutAtIndex(Node<$K$, $V$> keysValues, int index, $K$ key, $V$ value) {
            assert !canPutAtIndex(keysValues.size, index);
            assert keysValues.size == MAX_FANOUT; // i.e. implies index < 0

            int insertionPoint = -(index + 1);
            final Node<$K$, $V$> l = new Node<$K$, $V$>(), r = new Node<$K$, $V$>();
            l.size = r.size = MIN_FANOUT;

            if (insertionPoint < MIN_FANOUT) {
                copy(keysValues, 0,                           l, 0,                  insertionPoint);
                copy(keysValues, insertionPoint,              l, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);
                copy(keysValues, MIN_FANOUT - 1,              r, 0,                  MIN_FANOUT);

                l.setKey  (insertionPoint, key);
                l.setValue(insertionPoint, value);
            } else {
                insertionPoint -= MIN_FANOUT;

                copy(keysValues, 0,                           l, 0,                  MIN_FANOUT);
                copy(keysValues, MIN_FANOUT,                  r, 0,                  insertionPoint);
                copy(keysValues, MIN_FANOUT + insertionPoint, r, insertionPoint + 1, MIN_FANOUT - insertionPoint - 1);

                r.setKey  (insertionPoint, key);
                r.setValue(insertionPoint, value);
            }

            return new BubbledInsertion<$K$>(l, r, r.getKey(0));
        }
    }

    private static class Internal {
        private Internal() {}

        private static <$K$> $K$ getKey(Node<$K$, AbstractNode> repr, int index) {
            return repr.getKey(index);
        }

        private static <$K$> AbstractNode getNode(Node<$K$, AbstractNode> repr, int index) {
            return (AbstractNode)repr.getValue(index);
        }

        /** Always returns a valid index into the nodes array. Keys in the node indicated in the index will be >= key */
        public static <$K$> int find(Node<$K$, AbstractNode> repr, @Erased $K$ key, {{K_}}Comparator comparator) {
            final int size = repr.size;
            if (BINARY_SEARCH) {
                final int index = repr.binarySearch(0, size - 1, key, comparator);
                return index < 0 ? -(index + 1) : index + 1;
            } else if (comparator == null) {
                // Tried not exiting early (improves branch prediction) but significantly worse.
                int i;
                for (i = 0; i < size - 1; i++) {
                    final $K$ checkKey = repr.getKey(i);
                    {% if K.isPrimitive %}
                    // Avoiding the cast to Comparable is EXTREMELY IMPORTANT for speed (worth 10x)
                    if (checkKey > key) {
                    {% else %}
                    if (((Comparable)checkKey).compareTo(key) > 0) {
                    {% endif %}
                        return i;
                    }
                }

                return i;
            } else {
                // Tried not exiting early (improves branch prediction) but significantly worse.
                int i;
                for (i = 0; i < size - 1; i++) {
                    final $K$ checkKey = repr.getKey(i);
                    if (comparator.compare{% if K.isPrimitive %}{{K.name}}{% endif %}(checkKey, key) > 0) {
                        return i;
                    }
                }

                return i;
            }
        }

        public static boolean canPutAtIndex(int size) {
            return size < MAX_FANOUT;
        }

        public static <$K$> void putAtIndex(Node<$K$, AbstractNode> repr, int index, BubbledInsertion<$K$> toBubble) {
            assert canPutAtIndex(repr.size);

            // Tree:         Bubbled input:
            //  Key   0 1     Key    S
            //  Node 0 1 2    Node 1A 1B
            // If inserting at nodeIndex 1, shuffle things to:
            //  Key   0  S  1
            //  Node 0 1A 1B 2

            final int size = repr.size++;
            {{KObject_}}Node.arraycopyKey  (repr,  index,     repr,  index + 1, size - index - 1);
            {{KObject_}}Node.arraycopyValue(repr,  index + 1, repr,  index + 2, size - index - 1);

            repr.setKey  (index    , toBubble.separator);
            repr.setValue(index    , toBubble.leftObjects);
            repr.setValue(index + 1, toBubble.rightObjects);
        }

        private static <$K$> void deleteAtIndex(Node<$K$, AbstractNode> node, int index) {
            final int size = --node.size;
            {{KObject_}}Node.arraycopyKey  (node, index,     node, index - 1, size - index);
            {{KObject_}}Node.arraycopyValue(node, index + 1, node, index,     size - index);

            // Avoid memory leaks
            {% if K.isObject %}
            node.setKey  (size - 1, null);
            {% endif %}
            node.setValue(size,     null);
        }

        public static <$K$> BubbledInsertion<$K$> bubblePutAtIndex(Node<$K$, AbstractNode> repr, int nodeIndex, BubbledInsertion<$K$> toBubble) {
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

            final Node<$K$, AbstractNode> l = new Node<$K$, AbstractNode>(), r = new Node<$K$, AbstractNode>();
            l.size = r.size = MIN_FANOUT;

            final $K$ separator;
            if (nodeIndex == MIN_FANOUT - 1) {
                separator = toBubble.separator;

                {{KObject_}}Node.arraycopyKey(repr, 0,              l, 0, MIN_FANOUT - 1);
                {{KObject_}}Node.arraycopyKey(repr, MIN_FANOUT - 1, r, 0, MIN_FANOUT - 1);

                {{KObject_}}Node.arraycopyValue(repr,  0,          l,      0, MIN_FANOUT - 1);
                {{KObject_}}Node.arraycopyValue(repr,  MIN_FANOUT, r,      1, MIN_FANOUT - 1);

                l.setValue(MIN_FANOUT - 1, toBubble.leftObjects);
                r.setValue(0,              toBubble.rightObjects);
            } else if (nodeIndex < MIN_FANOUT) {
                separator = getKey(repr, MIN_FANOUT - 2);

                {{KObject_}}Node.arraycopyKey(repr, 0,              l, 0,             nodeIndex);
                {{KObject_}}Node.arraycopyKey(repr, nodeIndex,      l, nodeIndex + 1, MIN_FANOUT - nodeIndex - 2);
                {{KObject_}}Node.arraycopyKey(repr, MIN_FANOUT - 1, r, 0,             MIN_FANOUT - 1);

                {{KObject_}}Node.arraycopyValue(repr,  0,              l,      0,             nodeIndex);
                {{KObject_}}Node.arraycopyValue(repr,  nodeIndex + 1,  l,      nodeIndex + 2, MIN_FANOUT - nodeIndex - 2);
                {{KObject_}}Node.arraycopyValue(repr,  MIN_FANOUT - 1, r,      0,             MIN_FANOUT);

                l.setKey  (nodeIndex,     toBubble.separator);
                l.setValue(nodeIndex,     toBubble.leftObjects);
                l.setValue(nodeIndex + 1, toBubble.rightObjects);
            } else {
                nodeIndex -= MIN_FANOUT;
                // i.e. 0 <= nodeIndex < MIN_FANOUT - 1

                separator = getKey(repr, MIN_FANOUT - 1);

                {{KObject_}}Node.arraycopyKey(repr, 0,                      l, 0,             MIN_FANOUT - 1);
                {{KObject_}}Node.arraycopyKey(repr, MIN_FANOUT,             r, 0,             nodeIndex);
                {{KObject_}}Node.arraycopyKey(repr, MIN_FANOUT + nodeIndex, r, nodeIndex + 1, MIN_FANOUT - nodeIndex - 2);

                {{KObject_}}Node.arraycopyValue(repr, 0,                          l, 0,             MIN_FANOUT);
                {{KObject_}}Node.arraycopyValue(repr, MIN_FANOUT,                 r, 0,             nodeIndex);
                {{KObject_}}Node.arraycopyValue(repr, MIN_FANOUT + nodeIndex + 1, r, nodeIndex + 2, MIN_FANOUT - nodeIndex - 2);

                r.setKey  (nodeIndex,     toBubble.separator);
                r.setValue(nodeIndex,     toBubble.leftObjects);
                r.setValue(nodeIndex + 1, toBubble.rightObjects);
            }

            return new BubbledInsertion<$K$>(l, r, separator);
        }
    }

    private final Comparator<? super $K$> comparator;

    // Allocate these lazily to optimize allocation of lots of empty BTreeMaps
    private AbstractNode rootObjects;

    private int depth; // Number of levels of internal nodes in the tree
    private int size;

    private {{KV_}}BTreeMap(Comparator<? super $K$> comparator) {
        this.comparator = comparator;
    }

    void checkAssumingKeysNonNull() {
        if (rootObjects != null) {
            checkCore(rootObjects, depth, null, null, Bound.MISSING, Bound.MISSING);
        }
    }

    private void checkInRange(@Boxed $K$ k, @Boxed $K$ min, @Boxed $K$ max, Bound minBound, Bound maxBound) {
        assert minBound.lt(min, k, comparator) && maxBound.lt(k, max, comparator);
    }

    private void checkCore(AbstractNode repr, int depth, @Boxed $K$ min, @Boxed $K$ max, Bound minBound, Bound maxBound) {
        final int size = repr.size; // FIXME: decide what to do here -- base class, or push into branches?
        assert size <= Node.MAX_FANOUT;
        if (depth == this.depth) {
            // The root node may be smaller than others
            if (depth > 0) {
                assert size >= 2;
            }
        } else {
            assert size >= Node.MIN_FANOUT;
        }

        if (depth == 0) {
            final Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;

            int i;
            for (i = 0; i < size; i++) {
                $K$ k = Leaf.getKey(leaf, i);
                checkInRange(k, min, max, minBound, maxBound);
                {% if K.isObject %}
                assert k != null;
                {% endif %}
            }

            // To avoid memory leaks
            for (; i < Node.MAX_FANOUT; i++) {
                {% if K.isObject %}
                assert Leaf.getKey(leaf, i) == null;
                {% endif %}
                {% if V.isObject %}
                assert Leaf.getValue(leaf, i) == null;
                {% endif %}
            }
        } else {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;

            {
                int i;
                for (i = 0; i < size - 1; i++) {
                    $K$ k = Internal.getKey(internal, i);
                    checkInRange(k, min, max, minBound, maxBound);
                    {% if K.isObject %}
                    assert k != null;
                    {% endif %}
                }

                {% if K.isObject %}
                // To avoid memory leaks
                for (; i < Node.MAX_FANOUT - 1; i++) {
                    assert Internal.getKey(internal, i) == null;
                }
                {% endif %}
            }

            {
                int i;
                checkCore    (Internal.getNode(internal, 0),        depth - 1, min,                             Internal.getKey(internal, 0), minBound,        Bound.EXCLUSIVE);
                for (i = 1; i < size - 1; i++) {
                    checkCore(Internal.getNode(internal, i),        depth - 1, Internal.getKey(internal, i - 1),    Internal.getKey(internal, i), Bound.INCLUSIVE, Bound.EXCLUSIVE);
                }
                checkCore    (Internal.getNode(internal, size - 1), depth - 1, Internal.getKey(internal, size - 2), max,                      Bound.INCLUSIVE, maxBound);

                // To avoid memory leaks
                for (i = size; i < Node.MAX_FANOUT; i++) {
                    assert Internal.getNode(internal, i) == null;
                }
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
    public Comparator<? super $K$> comparator() {
        return comparator;
    }

    @Override
    public void putAll(Map<? extends @Boxed $K$, ? extends @Boxed $V$> that) {
        if (that instanceof SortedMap && Objects.equals(this.comparator(), ((SortedMap)that).comparator())) {
            // TODO: fastpath? (Even faster if that instanceof {{KV_}}BTreeMap)
        }

        for (Map.Entry<? extends @Boxed $K$, ? extends @Boxed $V$> e : that.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }


    @Override
    public @Boxed $V$ get(Object key) {
        return getOrDefault(key, null);
    }

    {% if V.isPrimitive() %}

    /** Gets the value at the given key. If no such value was found, returns the most negative {@code {{V}}} value. */
    public $V$ get{{V.name}}(Object key) {
        return getOrDefault{{V.name}}(key, {{V.dfault}});
    }

    {% endif %}

    {% if K.isPrimitive() %}

    /** Gets the value at the given key. If no such value was found, returns null. */
    public @Boxed $V$ get($K$ key) {
        return getOrDefault(key, null);
    }

    {% endif %}

    {% if K.isPrimitive() and V.isPrimitive() %}

    /** Gets the value at the given key. If no such value was found, returns the most negative {@code {{V}}} value. */
    public $V$ get{{V.name}}($K$ key) {
        return getOrDefault{{V.name}}(key, {{V.dfault}});
    }

    {% endif %}


    private Node<$K$, $V$> findLeaf(@Erased $K$ key) {
        AbstractNode nextObjects = rootObjects;
        int depth = this.depth;

        if (nextObjects == null) {
            return null;
        }

        final Comparator<? super @Boxed $K$> comparator = this.comparator;
        while (depth-- > 0) {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)nextObjects;
            final int ix = Internal.find(internal, key, comparator);
            nextObjects = Internal.getNode(internal, ix);
        }

        return (Node<$K$, $V$>)nextObjects;
    }

    @Override
    public @Boxed $V$ getOrDefault(Object key, @Boxed $V$ dflt) {
        {% if K.isPrimitive() %}
        if (!(key instanceof @Boxed $K$)) return dflt;
        return getOrDefault(($K$)key, dflt);
        {% else %}
        final Node<$K$, $V$> leaf = findLeaf(key);

        if (leaf == null) return dflt;

        final int ix = Leaf.find(leaf, ({{K.erased}})key, this.comparator);
        if (ix < 0) {
            return dflt;
        } else {
            return Leaf.getValue(leaf, ix);
        }
        {% endif %}
    }

    {% if K.isPrimitive() %}

    /** Gets the value at the given key. If no such value was found, returns the specified default. */
    public @Boxed $V$ getOrDefault($K$ key, @Boxed $V$ dflt) {
        final Node<$K$, $V$> leaf = findLeaf(key);

        if (leaf == null) return dflt;

        final int ix = Leaf.find(leaf, key, this.comparator);
        if (ix < 0) {
            return dflt;
        } else {
            return Leaf.getValue(leaf, ix);
        }
    }

    {% endif %}

    {% if V.isPrimitive() %}

    /** Gets the value at the given key. If no such value was found, returns the most negative {@code {{V}}} value. */
    public $V$ getOrDefault{{V.name}}(Object key, $V$ dflt) {
        {% if K.isPrimitive() %}
        if (!(key instanceof @Boxed $K$)) return dflt;
        return getOrDefault{{V.name}}(($K$)key, dflt);
        {% else %}
        final Node<$K$, $V$> leaf = findLeaf(key);

        if (leaf == null) return dflt;

        final int ix = Leaf.find(leaf, key, this.comparator);
        if (ix < 0) {
            return dflt;
        } else {
            return Leaf.getValue(leaf, ix);
        }
        {% endif %}
    }

    {% endif %}

    {% if K.isPrimitive() and V.isPrimitive() %}

    /** Gets the value at the given key. If no such value was found, returns the specified default. */
    public $V$ getOrDefault{{V.name}}($K$ key, $V$ dflt) {
        final Node<$K$, $V$> leaf = findLeaf(key);

        if (leaf == null) return dflt;

        final int ix = Leaf.find(leaf, key, this.comparator);
        if (ix < 0) {
            return dflt;
        } else {
            return Leaf.getValue(leaf, ix);
        }
    }

    {% endif %}


    @Override
    public boolean containsKey(Object key) {
        {% if K.isPrimitive() %}
        if (!(key instanceof @Boxed $K$)) return false;
        return containsKey(($K$)key);
        {% else %}
        final Node<$K$, $V$> leaf = findLeaf(key);
        if (leaf == null) return false;

        final int ix = Leaf.find(leaf, key, comparator);
        return ix >= 0;
        {% endif %}
    }

    {% if K.isPrimitive() %}

    /** Returns true iff an entry exists in the map with the supplied key. */
    public boolean containsKey($K$ key) {
        final Node<$K$, $V$> leaf = findLeaf(key);
        if (leaf == null) return false;

        final int ix = Leaf.find(leaf, key, comparator);
        return ix >= 0;
    }

    {% endif %}


    {% if K.isObject() and V.isObject() %}
    @Override
    public @Boxed $V$ put(@Boxed $K$ key, @Boxed $V$ value) {
    {% else %}
    /** Adds a new entry to the map, and returns the old value associated with this key. If no prior entry existed, returns null. */
    public @Boxed $V$ put($K$ key, $V$ value) {
    {% endif %}
        if (tryPutIntoEmptyMap(key, value)) {
            return null;
        }

        final @Erased @Boxed $V$[] resultBox = new @Erased @Boxed $V$[1];
        final BubbledInsertion<$K$> toBubble = putInternal(key, value, rootObjects, this.depth, resultBox);
        if (toBubble == null) {
            return (@Boxed $V$)resultBox[0];
        } else {
            finishBubbling(toBubble);
            return null;
        }
    }

    {% if V.isPrimitive() %}
    /** Adds a new entry to the map, and returns the old value associated with this key. If no prior entry existed, returns the most negative {@code {{V}}} value. */
    public $V$ put{{V.name}}($K$ key, $V$ value) {
        if (tryPutIntoEmptyMap(key, value)) {
            return {{V.dfault}};
        }

        final $V$[] resultBox = new $V$[1];
        resultBox[0] = {{V.dfault}};
        final BubbledInsertion<$K$> toBubble = putInternal{{V.name}}(key, value, rootObjects, this.depth, resultBox);
        if (toBubble == null) {
            return resultBox[0];
        } else {
            finishBubbling(toBubble);
            return {{V.dfault}};
        }
    }
    {% endif %}

    {% if K.isPrimitive or V.isPrimitive %}
    /**
     * {@inheritDoc}
     *
     {% if K.isPrimitive and V.isPrimitive %}
     * @throws NullPointerException if either the key or value are null
     {% elseif K.isPrimitive %}
     * @throws NullPointerException if the key is null
     {% else %}
     * @throws NullPointerException if the value is null
     {% endif %}
     */
    @Override
    public @Boxed $V$ put(@Boxed $K$ key, @Boxed $V$ value) {
        return put(($K$)key, ($V$)value);
    }
    {% endif %}

    private boolean tryPutIntoEmptyMap($K$ key, $V$ value) {
        if (rootObjects != null) {
            return false;
        } else {
            final Node<$K$, $V$> leaf = new Node<$K$, $V$>();
            leaf.setKey(0, key);
            leaf.setValue(0, value);
            leaf.size = 1;

            rootObjects = leaf;
            this.size = 1;
            return true;
        }
    }

    private void finishBubbling(BubbledInsertion<$K$> toBubble) {
        final Node<$K$, AbstractNode> internal = new Node<$K$, AbstractNode>();
        internal.size = 2;
        internal.setKey  (0, toBubble.separator);
        internal.setValue(0, toBubble.leftObjects);
        internal.setValue(1, toBubble.rightObjects);

        this.rootObjects = internal;
        this.depth++;
    }

    private BubbledInsertion<$K$> putInternal($K$ key, $V$ value, AbstractNode nextObjects, int depth, @Erased @Boxed $V$[] resultBox) {
        if (depth == 0) {
            final Node<$K$, $V$> leaf = (Node<$K$, $V$>)nextObjects;
            final int nodeIndex = Leaf.find(leaf, key, comparator);
            if (nodeIndex < 0) this.size++;

            if (Leaf.canPutAtIndex(leaf.size, nodeIndex)) {
                if (nodeIndex >= 0) {
                    resultBox[0] = Leaf.putOverwriteIndex(leaf, nodeIndex, key, value);
                } else {
                    Leaf.putInsertIndex(leaf, nodeIndex, key, value);
                }

                return null;
            }

            return Leaf.bubblePutAtIndex(leaf, nodeIndex, key, value);
        } else {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)nextObjects;
            final int nodeIndex = Internal.find(internal, key, comparator);

            final BubbledInsertion<$K$> toBubble = putInternal(key, value, Internal.getNode(internal, nodeIndex), depth - 1, resultBox);
            return putInternalFinishInternal(internal, nodeIndex, toBubble);
        }
    }

    {% if V.isPrimitive() %}
    private BubbledInsertion<$K$> putInternal{{V.name}}($K$ key, $V$ value, AbstractNode nextObjects, int depth, $V$[] resultBox) {
        if (depth == 0) {
            final Node<$K$, $V$> leaf = (Node<$K$, $V$>)nextObjects;
            final int nodeIndex = Leaf.find(leaf, key, comparator);
            if (nodeIndex < 0) this.size++;

            if (Leaf.canPutAtIndex(leaf.size, nodeIndex)) {
                if (nodeIndex >= 0) {
                    resultBox[0] = Leaf.putOverwriteIndex(leaf, nodeIndex, key, value);
                } else {
                    Leaf.putInsertIndex(leaf, nodeIndex, key, value);
                }

                return null;
            }

            return Leaf.bubblePutAtIndex(leaf, nodeIndex, key, value);
        } else {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)nextObjects;
            final int nodeIndex = Internal.find(internal, key, comparator);

            final BubbledInsertion<$K$> toBubble = putInternal{{V.name}}(key, value, Internal.getNode(internal, nodeIndex), depth - 1, resultBox);
            return putInternalFinishInternal(internal, nodeIndex, toBubble);
        }
    }
    {% endif %}

    private BubbledInsertion<$K$> putInternalFinishInternal(Node<$K$, AbstractNode> internal, int nodeIndex, BubbledInsertion<$K$> toBubble) {
        if (toBubble == null) {
            return null;
        }

        if (Internal.canPutAtIndex(internal.size)) {
            Internal.putAtIndex(internal, nodeIndex, toBubble);
            return null;
        }

        return Internal.bubblePutAtIndex(internal, nodeIndex, toBubble);
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

    private static <$K$, $V$> String toStringInternal(AbstractNode repr, int depth) {
        if (depth == 0) {
            final Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < leaf.size; i++) {
                if (sb.length() != 0) sb.append(", ");
                sb.append(Leaf.getKey(leaf, i)).append(": ").append(Leaf.getValue(leaf, i));
            }

            return sb.toString();
        } else {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < internal.size; i++) {
                if (sb.length() != 0) {
                    sb.append(" |").append(Internal.getKey(internal, i - 1)).append("| ");
                }
                final AbstractNode node = Internal.getNode(internal, i);
                sb.append("{").append(toStringInternal(node, depth - 1)).append("}");
            }

            return sb.toString();
        }
    }


    {% if K.isPrimitive() %}
    static <$K$, $V$> $K$ getEntryKey{{K.name}}(Entry<@Boxed $K$, @Boxed $V$> e) {
        return e == null ? {{K.dfault}} : ($K$)e.getKey();
    }
    {% endif %}

    static <K, V> K getEntryKey(Entry<K, V> e) {
        return e == null ? null : e.getKey();
    }


    {% for keyMethod in ["lower", "higher", "floor", "ceiling"] %}

    {% if K.isPrimitive() %}
    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the key is null
     */
    @Override
    public @Boxed $K$ {{keyMethod}}Key(@Boxed $K$ key) {
        return {{keyMethod}}Key(($K$)key);
    }

    /** Returns the key of the entry returned by {@link #{{keyMethod}}Entry}, or the most negative {@code {{K}}} value if no such entry exists */
    public $K$ {{keyMethod}}Key{{K.name}}($K$ key) {
        return getEntryKey{{K.name}}({{keyMethod}}Entry(key));
    }

    /** Returns the key of the entry returned by {@link #{{keyMethod}}Entry}, or null if no such entry exists */
    {% else %}
    @Override
    {% endif %}
    public @Boxed $K$ {{keyMethod}}Key($K$ key) {
        return getEntryKey({{keyMethod}}Entry(key));
    }

    {% endfor %}


    {% if K.isPrimitive() %}
    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if key is null
     */
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> lowerEntry(@Boxed $K$ key) {
        return lowerEntry(($K$)key);
    }

    /** Returns the entry with largest key strictly less than {@code key}, or null if no such entry exists */
    public Entry<@Boxed $K$, @Boxed $V$> lowerEntry($K$ key) {
    {% else %}
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> lowerEntry($K$ key) {
    {% endif %}
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node<$K$, AbstractNode> backtrackParent = null; // Deepest internal node on the path to "key" which has a child prior to the one we descended into
        int backtrackIndex = -1;                  // Index of that prior child
        int backtrackDepth = -1;                  // Depth of that internal node

        AbstractNode repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
            final int index = Internal.find(internal, key, comparator);
            if (index > 0) {
                backtrackParent = internal;
                backtrackIndex = index - 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(internal, index);
        }

        Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;
        final int leafIndex = Leaf.find(leaf, key, comparator);
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
                final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
                repr = Internal.getNode(internal, index);
                index = repr.size - 1;
            }

            returnIndex = index;
            leaf = (Node<$K$, $V$>)repr;
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            Leaf.getKey  (leaf, returnIndex),
            Leaf.getValue(leaf, returnIndex)
        );
    }

    {% if K.isPrimitive() %}
    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if key is null
     */
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> floorEntry(@Boxed $K$ key) {
        return floorEntry(($K$)key);
    }

    /** Returns entry with largest key less than or equal to {@code key}, or null if no such entry exists */
    public Entry<@Boxed $K$, @Boxed $V$> floorEntry($K$ key) {
    {% else %}
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> floorEntry($K$ key) {
    {% endif %}
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node<$K$, AbstractNode> backtrackParent = null; // Deepest internal node on the path to "key" which has a child prior to the one we descended into
        int backtrackIndex = -1;                  // Index of that prior child
        int backtrackDepth = -1;                  // Depth of that internal node

        AbstractNode repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
            final int index = Internal.find(internal, key, comparator);
            if (index > 0) {
                backtrackParent = internal;
                backtrackIndex = index - 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(internal, index);
        }

        Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;
        final int leafIndex = Leaf.find(leaf, key, comparator);
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
                    final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
                    repr = Internal.getNode(internal, index);
                    index = repr.size - 1;
                }

                leaf = (Node<$K$, $V$>)repr;
                returnIndex = index;
            }
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            Leaf.getKey  (leaf, returnIndex),
            Leaf.getValue(leaf, returnIndex)
        );
    }

    {% if K.isPrimitive() %}
    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if key is null
     */
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> ceilingEntry(@Boxed $K$ key) {
        return ceilingEntry(($K$)key);
    }

    /** Returns the entry with smallest key greater than or equal to {@code key}, or null if no such entry exists */
    public Entry<@Boxed $K$, @Boxed $V$> ceilingEntry($K$ key) {
    {% else %}
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> ceilingEntry($K$ key) {
    {% endif %}
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node<$K$, AbstractNode> backtrackParent = null; // Deepest internal node on the path to "key" which has a child next to the one we descended into
        int backtrackIndex = -1;                  // Index of that next child
        int backtrackDepth = -1;                  // Depth of that internal node

        AbstractNode repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
            final int index = Internal.find(internal, key, comparator);
            if (index < internal.size - 1) {
                backtrackParent = internal;
                backtrackIndex = index + 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(internal, index);
        }

        Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;
        final int leafIndex = Leaf.find(leaf, key, comparator);
        final int returnIndex;
        if (leafIndex >= 0) {
            returnIndex = leafIndex;
        } else {
            final int insertionPoint = -(leafIndex + 1);
            if (insertionPoint < leaf.size) {
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
                    final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
                    repr = Internal.getNode(internal, index);
                    index = 0;
                }

                leaf = (Node<$K$, $V$>)repr;
                returnIndex = index;
            }
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            Leaf.getKey  (leaf, returnIndex),
            Leaf.getValue(leaf, returnIndex)
        );
    }

    {% if K.isPrimitive() %}
    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if key is null
     */
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> higherEntry(@Boxed $K$ key) {
        return higherEntry(($K$)key);
    }

    /** Returns the entry with smallest key strictly greater than {@code key}, or null if no such entry exists */
    public Entry<@Boxed $K$, @Boxed $V$> higherEntry($K$ key) {
    {% else %}
    @Override
    public Entry<@Boxed $K$, @Boxed $V$> higherEntry($K$ key) {
    {% endif %}
        if (rootObjects == null) {
            return null;
        }

        final int depth = this.depth;

        Node<$K$, AbstractNode> backtrackParent = null; // Deepest internal node on the path to "key" which has a child next to the one we descended into
        int backtrackIndex = -1;                  // Index of that next child
        int backtrackDepth = -1;                  // Depth of that internal node

        AbstractNode repr = rootObjects;
        for (int i = 0; i < depth; i++) {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
            final int index = Internal.find(internal, key, comparator);
            if (index < internal.size - 1) {
                backtrackParent = internal;
                backtrackIndex = index + 1;
                backtrackDepth = i;
            }
            repr = Internal.getNode(internal, index);
        }

        Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;
        final int leafIndex = Leaf.find(leaf, key, comparator);
        final int insertionPoint = leafIndex >= 0 ? leafIndex + 1 : -(leafIndex + 1);
        final int returnIndex;
        if (insertionPoint < leaf.size) {
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
                final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
                repr = Internal.getNode(internal, index);
                index = 0;
            }

            leaf = (Node<$K$, $V$>)repr;
            returnIndex = index;
        }

        return new AbstractMap.SimpleImmutableEntry<>(
            Leaf.getKey  (leaf, returnIndex),
            Leaf.getValue(leaf, returnIndex)
        );
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> firstEntry() {
        if (rootObjects == null) {
            return null;
        }

        AbstractNode repr = rootObjects;
        int depth = this.depth;

        while (depth-- > 0) {
            final int index = 0;
            repr = Internal.getNode((Node<$K$, AbstractNode>)repr, index);
        }

        final Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;
        final int size = leaf.size;
        if (size == 0) {
            return null;
        } else {
            final int index = 0;
            return new AbstractMap.SimpleImmutableEntry<>(
                Leaf.getKey  (leaf, index),
                Leaf.getValue(leaf, index)
            );
        }
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> lastEntry() {
        if (rootObjects == null) {
            return null;
        }

        AbstractNode repr = rootObjects;
        int depth = this.depth;

        while (depth-- > 0) {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
            final int index = internal.size - 1;
            repr = Internal.getNode(internal, index);
        }

        final Node<$K$, $V$> leaf = (Node<$K$, $V$>)repr;
        final int size = leaf.size;
        if (size == 0) {
            return null;
        } else {
            final int index = size - 1;
            return new AbstractMap.SimpleImmutableEntry<>(
                Leaf.getKey  (leaf, index),
                Leaf.getValue(leaf, index)
            );
        }
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> pollFirstEntry() {
        // TODO: fast path?
        final Entry<@Boxed $K$, @Boxed $V$> e = firstEntry();
        if (e != null) {
            remove(e.getKey());
        }

        return e;
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> pollLastEntry() {
        // TODO: fast path?
        final Entry<@Boxed $K$, @Boxed $V$> e = lastEntry();
        if (e != null) {
            remove(e.getKey());
        }

        return e;
    }

    @Override
    public NavigableMap<@Boxed $K$, @Boxed $V$> descendingMap() {
        return new DescendingNavigableMap<@Boxed $K$, @Boxed $V$>(this.asNavigableMap2());
    }

    @Override
    public NavigableSet<@Boxed $K$> navigableKeySet() {
        return new NavigableMapKeySet<@Boxed $K$>(this);
    }

    @Override
    public NavigableSet<@Boxed $K$> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    @Override
    public NavigableMap<@Boxed $K$, @Boxed $V$> subMap(@Boxed $K$ fromKey, boolean fromInclusive, @Boxed $K$ toKey, boolean toInclusive) {
        return asNavigableMap2().subMap(fromKey, fromInclusive, toKey, toInclusive).asNavigableMap();
    }

    @Override
    public NavigableMap<@Boxed $K$, @Boxed $V$> headMap(@Boxed $K$ toKey, boolean inclusive) {
        return asNavigableMap2().headMap(toKey, inclusive).asNavigableMap();
    }

    @Override
    public NavigableMap<@Boxed $K$, @Boxed $V$> tailMap(@Boxed $K$ fromKey, boolean inclusive) {
        return asNavigableMap2().tailMap(fromKey, inclusive).asNavigableMap();
    }

    @Override
    public SortedMap<@Boxed $K$, @Boxed $V$> subMap(@Boxed $K$ fromKey, @Boxed $K$ toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    @Override
    public SortedMap<@Boxed $K$, @Boxed $V$> headMap(@Boxed $K$ toKey) {
        return headMap(toKey, false);
    }

    @Override
    public SortedMap<@Boxed $K$, @Boxed $V$> tailMap(@Boxed $K$ fromKey) {
        return tailMap(fromKey, true);
    }

    {% for fl in ["first", "last"] %}

    @Override
    public @Boxed $K$ {{fl}}Key() {
        final Entry<@Boxed $K$, @Boxed $V$> e = {{fl}}Entry();
        if (e == null) throw new NoSuchElementException();

        return e.getKey();
    }

    /**
     * Returns the key of the entry that would be returned by {@link #{{fl}}Entry}
     *
     * @throws NoSuchElementException if the map is empty
     */
    public $K$ {{fl}}Key{{K.name}}() {
        final Entry<@Boxed $K$, @Boxed $V$> e = {{fl}}Entry();
        if (e == null) throw new NoSuchElementException();

        // TODO: fast path avoiding allocation of Entry?
        return ($K$)e.getKey();
    }

    {% endfor %}

    @Override
    public NavigableSet<@Boxed $K$> keySet() {
        return navigableKeySet();
    }

    @Override
    public Collection<@Boxed $V$> values() {
        return new MapValueCollection<>(this);
    }

    private class EntryIterator implements Iterator<Entry<@Boxed $K$, @Boxed $V$>> {
        // indexes[0] is an index into rootObjects.
        // indexes[i] is an index into nodes[i - 1] (for i >= 1)
        private final int[] indexes = new int[depth + 1];
        private final AbstractNode[] nodes = new AbstractNode[depth];
        // If nextLevel >= 0:
        //   1. indexes[nextLevel] < size - 1
        //   2. There is no level l > nextLevel such that indexes[l] < size - 1
        private int nextLevel;
        private boolean hasNext;

        public void positionAtFirst() {
            nextLevel = -1;
            hasNext = false;
            if (rootObjects != null) {
                AbstractNode node = rootObjects;
                for (int i = 0;; i++) {
                    final int index = indexes[i] = 0;
                    if (index < node.size - 1) {
                        nextLevel = i;
                    }

                    if (i >= nodes.length) {
                        break;
                    }

                    node = nodes[i] = Internal.getNode((Node<$K$, AbstractNode>)node, index);
                }

                hasNext = node.size > 0;
            }
        }

        private Node<$K$, $V$> findLeaf($K$ key) {
            if (rootObjects == null) {
                return null;
            }

            AbstractNode repr = rootObjects;
            for (int i = 0; i < nodes.length; i++) {
                final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
                final int index = indexes[i] = Internal.find(internal, key, comparator);
                if (index < internal.size - 1) {
                    nextLevel = i;
                    // backtrackParent == nextLevel == 0 ? rootObjects : nodes[nextLevel - 1]
                    // backtrackIndex  == indexes[nextLevel] + 1
                }
                repr = nodes[i] = Internal.getNode(internal, index);
            }

            return (Node<$K$, $V$>)repr;
        }

        private void findNextLevel() {
            for (int i = indexes.length - 1; i >= 0; i--) {
                final AbstractNode node = i == 0 ? rootObjects : nodes[i - 1];
                if (indexes[i] < node.size - 1) {
                    nextLevel = i;
                    break;
                }
            }
        }

        private void positionAtIndex(AbstractNode repr, int returnIndex) {
            if (returnIndex >= repr.size) {
                // We need to find the first item in the next leaf node.
                int i = nextLevel;
                if (i < 0) {
                    // Oh -- that was the last leaf node
                    return;
                }

                repr = i == 0 ? rootObjects : nodes[i - 1];
                int index = indexes[i] + 1;
                for (; i < nodes.length; i++) {
                    indexes[i] = index;
                    repr = nodes[i] = Internal.getNode((Node<$K$, AbstractNode>)repr, index);
                    index = 0;
                }

                returnIndex = index;
                nextLevel = Integer.MIN_VALUE;
            }

            hasNext = true;
            indexes[nodes.length] = returnIndex;

            // Restore the nextLevel invariant
            if (returnIndex < repr.size - 1) {
                nextLevel = nodes.length;
            } else if (nextLevel == Integer.MIN_VALUE) {
                // We already "used up" backtrackDepth
                findNextLevel();
            }
        }

        public void positionAtCeiling($K$ key) {
            nextLevel = -1;
            hasNext = false;

            final Node<$K$, $V$> leaf = findLeaf(key);
            if (leaf == null) {
                return;
            }

            final int leafIndex = Leaf.find(leaf, key, comparator);
            positionAtIndex(leaf, leafIndex >= 0 ? leafIndex : -(leafIndex + 1));
        }

        public void positionAtHigher($K$ key) {
            nextLevel = -1;
            hasNext = false;

            final Node<$K$, $V$> leaf = findLeaf(key);
            if (leaf == null) {
                return;
            }

            final int leafIndex = Leaf.find(leaf, key, comparator);
            positionAtIndex(leaf, leafIndex >= 0 ? leafIndex + 1 : -(leafIndex + 1));
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public Entry<@Boxed $K$, @Boxed $V$> next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }

            final Entry<@Boxed $K$, @Boxed $V$> result;
            {
                final Node<$K$, $V$> leafNode = (Node<$K$, $V$>)(nodes.length == 0 ? rootObjects : nodes[nodes.length - 1]);
                final int ix = indexes[indexes.length - 1];
                result = new AbstractMap.SimpleImmutableEntry<@Boxed $K$, @Boxed $V$>(
                        Leaf.getKey(leafNode, ix),
                        Leaf.getValue(leafNode, ix)
                );
            }

            if (nextLevel < 0) {
                hasNext = false;
            } else {
                int index = ++indexes[nextLevel];
                AbstractNode node = nextLevel == 0 ? rootObjects : nodes[nextLevel - 1];
                assert index < node.size;
                if (nextLevel < nodes.length) {
                    // We stepped forward to a later item in an internal node: update all children
                    for (int i = nextLevel; i < nodes.length;) {
                        node = nodes[i++] = Internal.getNode((Node<$K$, AbstractNode>)node, index);
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
            // FIXME
            throw new UnsupportedOperationException("Iterator.remove() isn't supported yet, but I wouldn't be averse to adding it.");
        }
    }

    Iterator<Entry<@Boxed $K$, @Boxed $V$>> firstIterator() {
        final EntryIterator it = new EntryIterator();
        it.positionAtFirst();
        return it;
    }

    Iterator<Entry<@Boxed $K$, @Boxed $V$>> ceilingIterator($K$ k) {
        final EntryIterator it = new EntryIterator();
        it.positionAtCeiling(k);
        return it;
    }

    Iterator<Entry<@Boxed $K$, @Boxed $V$>> higherIterator($K$ k) {
        final EntryIterator it = new EntryIterator();
        it.positionAtHigher(k);
        return it;
    }

    private class DescendingEntryIterator implements Iterator<Entry<@Boxed $K$, @Boxed $V$>> {
        // indexes[0] is an index into rootObjects.
        // indexes[i] is an index into nodes[i - 1] (for i >= 1)
        private final int[] indexes = new int[depth + 1];
        private final AbstractNode[] nodes = new AbstractNode[depth];
        // If nextLevel >= 0:
        //   1. indexes[nextLevel] > 0
        //   2. There is no level l > nextLevel such that indexes[l] > 0
        private int nextLevel;
        private boolean hasNext;

        public void positionAtLast() {
            nextLevel = -1;
            hasNext = false;
            if (rootObjects != null) {
                AbstractNode node = rootObjects;
                for (int i = 0;; i++) {
                    final int index = indexes[i] = node.size - 1;
                    if (index > 0) {
                        nextLevel = i;
                    }

                    if (i >= nodes.length) {
                        break;
                    }

                    node = nodes[i] = Internal.getNode((Node<$K$, AbstractNode>)node, index);
                }

                hasNext = node.size > 0;
            }
        }

        private Node<$K$, $V$> findLeaf($K$ key) {
            if (rootObjects == null) {
                return null;
            }

            AbstractNode repr = rootObjects;
            for (int i = 0; i < nodes.length; i++) {
                final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
                final int index = indexes[i] = Internal.find(internal, key, comparator);
                if (index > 0) {
                    nextLevel = i;
                }
                repr = nodes[i] = Internal.getNode(internal, index);
            }

            return (Node<$K$, $V$>)repr;
        }

        private void positionAtIndex(int returnIndex) {
            if (returnIndex < 0) {
                // We need to find the last item in the prior leaf node.
                int i = nextLevel;
                if (i < 0) {
                    // Oh -- that was the first leaf node
                    return;
                }

                AbstractNode repr = i == 0 ? rootObjects : nodes[i - 1];
                int index = indexes[i] - 1;
                for (; i < nodes.length; i++) {
                    final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)repr;
                    indexes[i] = index;
                    repr = nodes[i] = Internal.getNode(internal, index);
                    index = repr.size - 1;
                }

                returnIndex = index;
                nextLevel = Integer.MIN_VALUE;
            }

            hasNext = true;
            indexes[nodes.length] = returnIndex;

            // Restore the nextLevel invariant
            if (returnIndex > 0) {
                nextLevel = nodes.length;
            } else if (nextLevel == Integer.MIN_VALUE) {
                // We already "used up" backtrackDepth
                for (int i = indexes.length - 1; i >= 0; i--) {
                    if (indexes[i] > 0) {
                        nextLevel = i;
                        break;
                    }
                }
            }
        }

        public void positionAtFloor($K$ key) {
            nextLevel = -1;
            hasNext = false;

            final Node<$K$, $V$> leaf = findLeaf(key);
            if (leaf == null) {
                return;
            }

            final int leafIndex = Leaf.find(leaf, key, comparator);
            positionAtIndex(leafIndex >= 0 ? leafIndex : -(leafIndex + 1) - 1);
        }

        public void positionAtLower($K$ key) {
            nextLevel = -1;
            hasNext = false;

            final Node<$K$, $V$> leaf = findLeaf(key);
            if (leaf == null) {
                return;
            }

            final int leafIndex = Leaf.find(leaf, key, comparator);
            positionAtIndex(leafIndex >= 0 ? leafIndex - 1 : -(leafIndex + 1) - 1);
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public Entry<@Boxed $K$, @Boxed $V$> next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }

            final Entry<@Boxed $K$, @Boxed $V$> result;
            {
                final Node<$K$, $V$> leafNode = (Node<$K$, $V$>)(nodes.length == 0 ? rootObjects : nodes[nodes.length - 1]);
                final int ix = indexes[indexes.length - 1];
                result = new AbstractMap.SimpleImmutableEntry<@Boxed $K$, @Boxed $V$>(
                        Leaf.getKey(leafNode, ix),
                        Leaf.getValue(leafNode, ix)
                );
            }

            if (nextLevel < 0) {
                hasNext = false;
            } else {
                int index = --indexes[nextLevel];
                assert index >= 0;
                if (nextLevel < nodes.length) {
                    // We stepped back to an earlier item in an internal node: update all children
                    AbstractNode node = nextLevel == 0 ? rootObjects : nodes[nextLevel - 1];
                    for (int i = nextLevel; i < nodes.length;) {
                        node = nodes[i++] = Internal.getNode((Node<$K$, AbstractNode>)node, index);
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
            // FIXME
            throw new UnsupportedOperationException("Iterator.remove() isn't supported yet, but I wouldn't be averse to adding it.");
        }
    }

    Iterator<Entry<@Boxed $K$, @Boxed $V$>> lastIterator() {
        final DescendingEntryIterator it = new DescendingEntryIterator();
        it.positionAtLast();;
        return it;
    }

    Iterator<Entry<@Boxed $K$, @Boxed $V$>> lowerIterator($K$ key) {
        final DescendingEntryIterator it = new DescendingEntryIterator();
        it.positionAtLower(key);
        return it;
    }

    Iterator<Entry<@Boxed $K$, @Boxed $V$>> floorIterator($K$ key) {
        final DescendingEntryIterator it = new DescendingEntryIterator();
        it.positionAtFloor(key);
        return it;
    }

    @Override
    public Set<Entry<@Boxed $K$, @Boxed $V$>> entrySet() {
        return new MapEntrySet<>(this, this::firstIterator);
    }

    NavigableMap2<@Boxed $K$, @Boxed $V$> asNavigableMap2() {
        return new NavigableMap2<@Boxed $K$, @Boxed $V$>() {
            @Override
            public NavigableMap<@Boxed $K$, @Boxed $V$> asNavigableMap() {
                return {{KV_}}BTreeMap.this;
            }

            @Override
            public Set<Entry<@Boxed $K$, @Boxed $V$>> descendingEntrySet() {
                return new MapEntrySet<>({{KV_}}BTreeMap.this, {{KV_}}BTreeMap.this::lastIterator);
            }

            @Override
            public NavigableMap2<@Boxed $K$, @Boxed $V$> subMap(@Boxed $K$ fromKey, boolean fromInclusive, @Boxed $K$ toKey, boolean toInclusive) {
                return new RestrictedBTreeMap<$K$, $V$>(
                        {{KV_}}BTreeMap.this, fromKey, toKey,
                        Bound.inclusive(fromInclusive),
                        Bound.inclusive(toInclusive)).asNavigableMap2();
            }

            @Override
            public NavigableMap2<@Boxed $K$, @Boxed $V$> headMap(@Boxed $K$ toKey, boolean inclusive) {
                return new RestrictedBTreeMap<$K$, $V$>(
                        {{KV_}}BTreeMap.this, null, toKey,
                        Bound.MISSING,
                        Bound.inclusive(inclusive)).asNavigableMap2();
            }

            @Override
            public NavigableMap2<@Boxed $K$, @Boxed $V$> tailMap(@Boxed $K$ fromKey, boolean inclusive) {
                return new RestrictedBTreeMap<$K$, $V$>(
                        {{KV_}}BTreeMap.this, fromKey, null,
                        Bound.inclusive(inclusive),
                        Bound.MISSING).asNavigableMap2();
            }
        };
    }

    {% if V.isPrimitive() %}
    /** Remove the entry with the specified key, returning the value of the removed entry. If no such entry existed, returns the most negative {@code {{V}}} value. */
    public $V$ remove{{V.name}}(Object key) {
        // TODO: fast path avoiding allocation of result?
        final @Boxed $V$ result = remove(key);
        return result == null ? {{V.dfault}} : ($V$)result;
    }

    {% if K.isPrimitive() %}
    /** Remove the entry with the specified key, returning the value of the removed entry. If no such entry existed, returns the most negative {@code {{V}}} value. */
    public $V$ remove{{V.name}}($K$ key) {
        // TODO: fast path avoiding allocation of result?
        final @Boxed $V$ result = remove(key);
        return result == null ? {{V.dfault}} : ($V$)result;
    }
    {% endif %}
    {% endif %}

    {% if K.isPrimitive() %}
    @Override
    public @Boxed $V$ remove(Object key) {
        if (!(key instanceof @Boxed $K$)) {
            return null;
        } else {
            return remove(($K$)key);
        }
    }

    /** Remove the entry with the specified key, returning the value of the removed entry. If no such entry existed, returns null. */
    public @Boxed $V$ remove($K$ key) {
    {% else %}
    @Override
    public @Boxed $V$ remove(Object key) {
    {% endif %}
        if (rootObjects == null) {
            return null;
        }

        final @Boxed $V$ result = removeCore(rootObjects, depth, key);
        if (rootObjects.size == 1 && depth > 0) {
            rootObjects = Internal.getNode((Node<$K$, AbstractNode>)rootObjects, 0);
            depth--;
        }

        return result;
    }

    private @Boxed $V$ removeCore(Object node, int depth, @Erased $K$ key) {
        if (depth == 0) {
            final Node<$K$, $V$> leaf = (Node<$K$, $V$>)node;
            final int index = Leaf.find(leaf, key, comparator);
            if (index < 0) {
                return null;
            } else {
                final $V$ result = ($V$)Leaf.getValue(leaf, index);

                size--;
                leaf.size--;
                {{KV_}}Node.arraycopyKey  (leaf, index + 1, leaf, index, leaf.size - index);
                {{KV_}}Node.arraycopyValue(leaf, index + 1, leaf, index, leaf.size - index);

                // Avoid memory leaks
                {% if K.isObject() %}leaf.setKey  (leaf.size, null);{% endif %}
                {% if V.isObject() %}leaf.setValue(leaf.size, null);{% endif %}

                return result;
            }
        } else {
            final Node<$K$, AbstractNode> internal = (Node<$K$, AbstractNode>)node;
            final int index = Internal.find(internal, key, comparator);
            final AbstractNode child = Internal.getNode(internal, index);
            final @Boxed $V$ result = removeCore(child, depth - 1, key);

            if (child.size < Node.MIN_FANOUT) {
                assert child.size == Node.MIN_FANOUT - 1;

                if (index > 0) {
                    // Take key from or merge with predecessor
                    final AbstractNode pred = Internal.getNode(internal, index - 1);
                    if (pred.size > Node.MIN_FANOUT) {
                        // Can take key from predecessor
                        final int predSize = --pred.size;
                        final int childSize = child.size++;

                        final $K$ predLtKey;
                        if (depth == 1) {
                            // Children are leaves
                            final Node<$K$, $V$> childLeaf = (Node<$K$, $V$>)child;
                            final Node<$K$, $V$> predLeaf  = (Node<$K$, $V$>)pred;
                            predLtKey = predLeaf.getKey(predSize);
                            final $V$ predValue = predLeaf.getValue(predSize);

                            // Avoid memory leaks
                            {% if K.isObject() %}predLeaf.setKey  (predSize, null);{% endif %}
                            {% if V.isObject() %}predLeaf.setValue(predSize, null);{% endif %}

                            {{KV_}}Node.arraycopyKey  (childLeaf, 0, childLeaf, 1, childSize);
                            {{KV_}}Node.arraycopyValue(childLeaf, 0, childLeaf, 1, childSize);
                            childLeaf.setKey  (0, predLtKey);
                            childLeaf.setValue(0, predValue);
                        } else {
                            // Children are internal nodes
                            final Node<$K$, AbstractNode> childInternal = (Node<$K$, AbstractNode>)child;
                            final Node<$K$, AbstractNode> predInternal  = (Node<$K$, AbstractNode>)pred;
                            predLtKey = Internal.getKey(predInternal, predSize - 1);
                            final $K$ predKey = Internal.getKey(internal, index - 1);
                            final AbstractNode predNode = Internal.getNode(predInternal, predSize);

                            // Avoid memory leaks
                            {% if K.isObject %}
                            predInternal.setKey  (predSize - 1, null);
                            {% endif %}
                            predInternal.setValue(predSize,     null);

                            {{KObject_}}Node.arraycopyKey  (childInternal, 0, childInternal, 1,  childSize - 1);
                            {{KObject_}}Node.arraycopyValue(childInternal, 0, childInternal, 1, childSize);
                            childInternal.setKey  (0, predKey);
                            childInternal.setValue(0, predNode);
                        }

                        internal.setKey(index - 1, predLtKey);
                    } else {
                        // Can merge with predecessor
                        final $K$ middleKey = Internal.getKey(internal, index - 1);
                        Internal.deleteAtIndex(internal, index);
                        appendToPred(pred, middleKey, child, depth - 1);
                    }
                } else {
                    // Take key from or merge with successor (there must be one because all nodes except the root must have at least 1 sibling)
                    final AbstractNode succ = Internal.getNode(internal, index + 1);
                    if (succ.size > Node.MIN_FANOUT) {
                        // Can take key from successor
                        final int succSize = --succ.size;
                        final int childSize = child.size++;

                        final $K$ succGteKey;
                        if (depth == 1) {
                            // Children are leaves
                            final Node<$K$, $V$> childLeaf = (Node<$K$, $V$>)child;
                            final Node<$K$, $V$> succLeaf  = (Node<$K$, $V$>)succ;
                            succGteKey = Leaf.getKey(succLeaf, 1);
                            final $K$ succKey   = succLeaf.getKey  (0);
                            final $V$ succValue = succLeaf.getValue(0);

                            {{KV_}}Node.arraycopyKey  (succLeaf, 1, succLeaf, 0, succSize);
                            {{KV_}}Node.arraycopyValue(succLeaf, 1, succLeaf, 0, succSize);

                            // Avoid memory leaks
                            {% if K.isObject %}succLeaf.setKey  (succSize, null);{% endif %}
                            {% if V.isObject %}succLeaf.setValue(succSize, null);{% endif %}

                            childLeaf.setKey  (childSize, succKey);
                            childLeaf.setValue(childSize, succValue);
                        } else {
                            // Children are internal nodes
                            final Node<$K$, AbstractNode> childInternal = (Node<$K$, AbstractNode>)child;
                            final Node<$K$, AbstractNode> succInternal  = (Node<$K$, AbstractNode>)succ;
                            succGteKey = Internal.getKey(succInternal, 0);
                            final $K$ succKey = Internal.getKey(internal, index);
                            final AbstractNode succNode = Internal.getNode(succInternal, 0);

                            {{KObject_}}Node.arraycopyKey  (succInternal, 1, succInternal, 0, succSize - 1);
                            {{KObject_}}Node.arraycopyValue(succInternal, 1, succInternal, 0, succSize);

                            // Avoid memory leaks
                            {% if K.isObject %}
                            succInternal.setKey  (succSize - 1, null);
                            {% endif %}
                            succInternal.setValue(succSize,     null);

                            childInternal.setKey  (childSize, succKey);
                            childInternal.setValue(childSize, succNode);
                        }

                        internal.setKey(index, succGteKey);
                    } else {
                        // Can merge with successor
                        final $K$ middleKey = Internal.getKey(internal, index);
                        Internal.deleteAtIndex(internal, index + 1);
                        appendToPred(child, middleKey, succ, depth - 1);
                    }
                }
            }

            return result;
        }
    }

    private void appendToPred(AbstractNode pred, $K$ middleKey, AbstractNode succ, int depth) {
        final int succSize = succ.size;
        final int predSize = pred.size;

        pred.size = predSize + succSize;
        assert pred.size == MAX_FANOUT;

        if (depth == 0) {
            // Children are leaves
            final Node<$K$, $V$> succLeaf = (Node<$K$, $V$>)succ,
                                 predLeaf = (Node<$K$, $V$>)pred;
            {{KV_}}Node.arraycopyKey  (succLeaf, 0, predLeaf, predSize, succSize);
            {{KV_}}Node.arraycopyValue(succLeaf, 0, predLeaf, predSize, succSize);
        } else {
            // Children are internal nodes
            final Node<$K$, AbstractNode> succInternal = (Node<$K$, AbstractNode>)succ,
                                          predInternal = (Node<$K$, AbstractNode>)pred;
            predInternal.setKey(predSize - 1, middleKey);
            {{KObject_}}Node.arraycopyKey  (succInternal, 0, predInternal, predSize, succSize - 1);
            {{KObject_}}Node.arraycopyValue(succInternal, 0, predInternal, predSize, succSize);
        }
    }
}
