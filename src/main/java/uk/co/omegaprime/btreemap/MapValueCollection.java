package uk.co.omegaprime.btreemap;

import java.util.*;

class MapValueCollection<V> implements Collection<V> {
    private final Map<?, V> that;

    public MapValueCollection(Map<?, V> that) {
        this.that = that;
    }

    @Override
    public String toString() {
        return Iterables.toString(this);
    }

    @Override
    public boolean equals(Object that) {
        return that instanceof Collection && equals((Collection)that);
    }

    private boolean equals(Collection that) {
        if (this.size() != that.size()) return false;

        // Seems dumb but suggested by the Javadoc for Collection.equals
        if (that instanceof Set || that instanceof List) return false;

        final Iterator<V> thisIt = this.iterator();
        final Iterator thatIt = that.iterator();

        while (thisIt.hasNext() && thatIt.hasNext()) {
            if (!Objects.equals(thisIt.next(), thatIt.next())) {
                return false;
            }
        }

        if (thisIt.hasNext() || thatIt.hasNext()) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (V v : this) {
            h += Objects.hashCode(v);
        }

        return h;
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
        for (V x : this) {
            if (Objects.equals(x, o)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Iterator<V> iterator() {
        final Iterator<? extends Map.Entry<?, V>> it = that.entrySet().iterator();
        return new Iterator<V>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public V next() {
                return it.next().getValue();
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
        for (V value : this) {
            result[i++] = value;
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
        for (V value : this) {
            //noinspection unchecked
            a[i++] = (T)value;
        }

        if (i < a.length) {
            a[i] = null;
        }

        return a;
    }

    @Override
    public boolean add(V v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        return that.entrySet().removeIf(e -> Objects.equals(o, e.getValue()));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        final Set<?> all = new LinkedHashSet<>(c);
        for (V x : this) {
            all.remove(x);
            if (all.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
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
        final Set<?> all = new LinkedHashSet<>(c);

        boolean changed = false;
        final Iterator<V> it = this.iterator();
        while (it.hasNext()) {
            if (removeIfMentioned == all.contains(it.next())) {
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
}
