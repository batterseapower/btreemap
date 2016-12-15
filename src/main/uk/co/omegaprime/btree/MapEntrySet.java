package uk.co.omegaprime.btree;

import java.util.*;

class MapEntrySet<K, V> implements Set<Map.Entry<K, V>> {
    private final Map<K, V> that;
    private final Iterable<Map.Entry<K, V>> iterable;

    public MapEntrySet(Map<K, V> that, Iterable<Map.Entry<K, V>> iterable) {
        this.that = that;
        this.iterable = iterable;
    }

    @Override
    public String toString() {
        return Iterables.toString(this);
    }

    @Override
    public boolean equals(Object that) {
        return Sets.equals(this, that);
    }

    @Override
    public int hashCode() {
        return Iterables.hashCode(this);
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
        if (!(o instanceof Map.Entry)) {
            return false;
        }

        return containsEntry((Map.Entry)o);
    }

    private boolean containsEntry(Map.Entry e) {
        return Objects.equals(e.getValue(), that.get(e.getKey())) &&
                (e.getValue() != null || that.containsKey(e.getKey()));
    }

    @Override
    public Object[] toArray() {
        final Object[] result = new Object[that.size()];
        int i = 0;
        for (Map.Entry<K, V> e : this) {
            result[i++] = e;
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
        for (Map.Entry<K, V> e : this) {
            //noinspection unchecked
            a[i++] = (T)e;
        }

        if (i < a.length) {
            a[i] = null;
        }

        return a;
    }

    @Override
    public boolean add(Map.Entry<K, V> kvEntry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof Map.Entry)) {
            return false;
        }

        final Map.Entry e = (Map.Entry)o;
        if (containsEntry(e)) {
            that.remove(e.getKey());
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
    public boolean addAll(Collection<? extends Map.Entry<K, V>> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return removeRetainAll(c, true);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return removeRetainAll(c, false);
    }

    private boolean removeRetainAll(Collection<?> c, boolean removeIfMentioned) {
        final Map<Object, Set<Object>> cMap = new HashMap<>();
        for (Object x : c) {
            if (!(x instanceof Map.Entry)) {
                continue;
            }

            final Map.Entry e = (Map.Entry)x;
            cMap.computeIfAbsent(e.getKey(), _key -> new HashSet<>()).add(e.getValue());
        }

        boolean changed = false;
        final Iterator<Map.Entry<K, V>> it = iterator();
        while (it.hasNext()) {
            final Map.Entry<K, V> e = it.next();
            if (removeIfMentioned == cMap.getOrDefault(e.getKey(), Collections.emptySet()).contains(e.getValue())) {
                it.remove();
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void clear() {
        that.clear();
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return iterable.iterator();
    }
}
