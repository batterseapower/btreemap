package uk.co.omegaprime.btree;

import java.util.*;

class MapValueCollection<V> implements Collection<V> {
    private final Map<?, V> that;

    public MapValueCollection(Map<?, V> that) {
        this.that = that;
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
        final Object[] result = new Object[that.size()];
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
        final Set<?> all = new LinkedHashSet<>();

        boolean changed = false;
        final Iterator<V> it = this.iterator();
        while (it.hasNext()) {
            if (all.contains(it.next())) {
                it.remove();
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        final Set<?> all = new LinkedHashSet<>();

        boolean changed = false;
        final Iterator<V> it = this.iterator();
        while (it.hasNext()) {
            if (!all.contains(it.next())) {
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
