package uk.co.omegaprime.btree;

import java.util.*;

class NavigableMapKeySet<K> implements NavigableSet<K> {
    private final NavigableMap<K, ?> that;

    NavigableMapKeySet(NavigableMap<K, ?> that) {
        this.that = that;
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public boolean equals(Object that) {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException(); // FIXME
    }

    @Override
    public K lower(K k) {
        return that.lowerKey(k);
    }

    @Override
    public K floor(K k) {
        return that.floorKey(k);
    }

    @Override
    public K ceiling(K k) {
        return that.ceilingKey(k);
    }

    @Override
    public K higher(K k) {
        return that.higherKey(k);
    }

    @Override
    public K pollFirst() {
        return BTreeMap.getEntryKey(that.pollFirstEntry());
    }

    @Override
    public K pollLast() {
        return BTreeMap.getEntryKey(that.pollLastEntry());
    }

    @Override
    public int size() {
        return that.size();
    }

    @Override
    public boolean isEmpty() {
        return that.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        //noinspection SuspiciousMethodCalls
        return that.containsKey(o);
    }

    @Override
    public Iterator<K> iterator() {
        final Iterator<? extends Map.Entry<K, ?>> it = that.entrySet().iterator();
        return new Iterator<K>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public K next() {
                return it.next().getKey();
            }

            @Override
            public void remove() {
                it.remove();
            }
        };
    }

    @Override
    public Object[] toArray() {
        final Object[] result = new Object[size()];
        int i = 0;
        for (K key : this) {
            result[i++] = key;
        }

        assert i == result.length;
        return result;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        final int size = size();
        if (a.length < size) {
            a = Arrays.copyOf(a, size);
        }

        int i = 0;
        for (K key : this) {
            //noinspection unchecked
            a[i++] = (T)key;
        }

        if (i < a.length) {
            a[i] = null;
        }

        return a;
    }

    @Override
    public boolean add(K k) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends K> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        //noinspection SuspiciousMethodCalls
        if (that.containsKey(o)) {
            that.remove(o);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return c.stream().allMatch(this::contains);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        // TODO: fast path?
        final Set<?> cSet = c instanceof Set ? (Set<?>)c : new HashSet<>(c);

        boolean changed = false;
        final Iterator<K> it = iterator();
        while (it.hasNext()) {
            final K k = it.next();
            if (!cSet.contains(k)) {
                changed = true;
                it.remove();
            }
        }

        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object x : c) {
            changed |= remove(x);
        }

        return changed;
    }

    @Override
    public void clear() {
        that.clear();
    }

    @Override
    public NavigableSet<K> descendingSet() {
        return new NavigableMapKeySet<>(that.descendingMap());
    }

    @Override
    public Iterator<K> descendingIterator() {
        return new NavigableMapKeySet<>(that.descendingMap()).iterator();
    }

    @Override
    public Comparator<? super K> comparator() {
        return that.comparator();
    }

    @Override
    public SortedSet<K> subSet(K fromElement, K toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public NavigableSet<K> subSet(K fromElement, boolean fromInclusive, K toElement, boolean toInclusive) {
        return new NavigableMapKeySet<>(that.subMap(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public SortedSet<K> headSet(K toElement) {
        return headSet(toElement, false);
    }

    @Override
    public NavigableSet<K> headSet(K toElement, boolean inclusive) {
        return new NavigableMapKeySet<>(that.headMap(toElement, inclusive));
    }

    @Override
    public SortedSet<K> tailSet(K fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public NavigableSet<K> tailSet(K fromElement, boolean inclusive) {
        return new NavigableMapKeySet<>(that.tailMap(fromElement, inclusive));
    }

    @Override
    public K first() {
        return that.firstKey();
    }

    @Override
    public K last() {
        return that.lastKey();
    }
}
