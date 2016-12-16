package uk.co.omegaprime.btree;

import java.util.*;

class RestrictedBTreeMap<K, V> implements NavigableMap2<K, V> {
    private static int cmp(Object l, Object r, Comparator c) {
        return c == null ? ((Comparable)l).compareTo(r) : c.compare(l, r);
    }

    public enum Bound {
        MISSING, INCLUSIVE, EXCLUSIVE;

        public static Bound inclusive(boolean flag) {
            return flag ? INCLUSIVE : EXCLUSIVE;
        }

        public boolean lt(Object x, Object y, Comparator c) {
            if (this == MISSING) return true;

            final int r = cmp(x, y, c);
            return r < 0 || (r == 0 && this == INCLUSIVE);
        }
    }

    private final BTreeMap<K, V> that;
    private final K min, max;
    private final Bound minBound, maxBound;

    public RestrictedBTreeMap(BTreeMap<K, V> that, K min, K max, Bound minBound, Bound maxBound) {
        // Map should still work fine if this invariant is violated, but:
        //   1. It might be less efficient than using "that" directly
        //   2. It's impossible for a user to construct such an instance right now
        assert minBound != Bound.MISSING || maxBound != Bound.MISSING;

        this.that = that;
        this.min = min;
        this.max = max;
        this.minBound = minBound;
        this.maxBound = maxBound;
    }

    private boolean inRange(Object that) {
        return minBound.lt(min, that, comparator()) && maxBound.lt(that, max, comparator());
    }

    @Override
    public Entry<K, V> lowerEntry(K key) {
        final Entry<K, V> e;
        if (maxBound == Bound.MISSING || Objects.compare(key, max, comparator()) <= 0) {
            e = that.lowerEntry(key);
        } else if (maxBound == Bound.INCLUSIVE) {
            e = that.floorEntry(max);
        } else {
            e = that.lowerEntry(max);
        }

        return e != null && minBound.lt(min, e.getKey(), comparator()) ? e : null;
    }

    @Override
    public K lowerKey(K key) {
        return BTreeMap.getEntryKey(lowerEntry(key));
    }

    @Override
    public Entry<K, V> floorEntry(K key) {
        final Entry<K, V> e;
        if (maxBound == Bound.MISSING || Objects.compare(key, max, comparator()) < 0) {
            e = that.floorEntry(key);
        } else if (maxBound == Bound.INCLUSIVE) {
            e = that.floorEntry(max);
        } else {
            e = that.lowerEntry(max);
        }

        return e != null && minBound.lt(min, e.getKey(), comparator()) ? e : null;
    }

    @Override
    public K floorKey(K key) {
        return BTreeMap.getEntryKey(floorEntry(key));
    }

    @Override
    public Entry<K, V> ceilingEntry(K key) {
        final Entry<K, V> e;
        if (minBound == Bound.MISSING || Objects.compare(min, key, comparator()) > 0) {
            e = that.ceilingEntry(key);
        } else if (minBound == Bound.INCLUSIVE) {
            e = that.ceilingEntry(min);
        } else {
            e = that.higherEntry(min);
        }

        return e != null && maxBound.lt(e.getKey(), max, comparator()) ? e : null;
    }

    @Override
    public K ceilingKey(K key) {
        return BTreeMap.getEntryKey(ceilingEntry(key));
    }

    @Override
    public Entry<K, V> higherEntry(K key) {
        final Entry<K, V> e;
        if (minBound == Bound.MISSING || Objects.compare(min, key, comparator()) >= 0) {
            e = that.higherEntry(key);
        } else if (minBound == Bound.INCLUSIVE) {
            e = that.ceilingEntry(min);
        } else {
            e = that.higherEntry(min);
        }

        return e != null && maxBound.lt(e.getKey(), max, comparator()) ? e : null;
    }

    @Override
    public K higherKey(K key) {
        return BTreeMap.getEntryKey(higherEntry(key));
    }

    @Override
    public Entry<K, V> firstEntry() {
        switch (minBound) {
            case MISSING:   return that.firstEntry();
            case INCLUSIVE: return that.ceilingEntry(min);
            case EXCLUSIVE: return that.higherEntry(min);
            default: throw new IllegalStateException();
        }
    }

    @Override
    public Entry<K, V> lastEntry() {
        switch (minBound) {
            case MISSING:   return that.lastEntry();
            case INCLUSIVE: return that.floorEntry(max);
            case EXCLUSIVE: return that.lowerEntry(max);
            default: throw new IllegalStateException();
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
        return new DescendingNavigableMap<>(this);
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        return new NavigableMapKeySet<>(this);
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        return descendingMap().descendingKeySet();
    }

    @Override
    public NavigableMap2<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return headMap(toKey, toInclusive).tailMap(fromKey, fromInclusive);
    }

    @Override
    public NavigableMap2<K, V> headMap(K toKey, boolean inclusive) {
        if (maxBound.lt(toKey, max, comparator())) {
            return new RestrictedBTreeMap<>(that, min, toKey, minBound, Bound.inclusive(inclusive));
        } else {
            return this;
        }
    }

    @Override
    public NavigableMap2<K, V> tailMap(K fromKey, boolean inclusive) {
        if (minBound.lt(min, fromKey, comparator())) {
            return new RestrictedBTreeMap<>(that, fromKey, max, Bound.inclusive(inclusive), maxBound);
        } else {
            return this;
        }
    }

    @Override
    public Comparator<? super K> comparator() {
        return that.comparator();
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
        return BTreeMap.getEntryKey(firstEntry());
    }

    @Override
    public K lastKey() {
        return BTreeMap.getEntryKey(lastEntry());
    }

    @Override
    public int size() {
        int i = 0;
        for (Map.Entry<K, V> _e : this.entrySet()) {
            i++;
        }

        return i;
    }

    @Override
    public boolean isEmpty() {
        return !entrySet().iterator().hasNext();
    }

    @Override
    public boolean containsKey(Object key) {
        return inRange(key) && that.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public V get(Object key) {
        return inRange(key) ? that.get(key) : null;
    }

    @Override
    public V put(K key, V value) {
        if (!inRange(key)) {
            throw new IllegalArgumentException("key out of range");
        }
        return that.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return inRange(key) ? that.remove(key) : null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        // TODO: fast path?
        final Iterator<Entry<K, V>> it = entrySet().iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    @Override
    public Set<K> keySet() {
        return navigableKeySet();
    }

    @Override
    public Collection<V> values() {
        return new MapValueCollection<V>(this);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new MapEntrySet<>(this, () -> {
            final Iterator<Entry<K, V>> it;
            switch (minBound) {
                case MISSING:   it = that.firstIterator(); break;
                case INCLUSIVE: it = that.ceilingIterator(min); break;
                case EXCLUSIVE: it = that.higherIterator(min); break;
                default: throw new IllegalStateException();
            }

            switch (maxBound) {
                case MISSING:   return it;
                case INCLUSIVE: return Iterators.takeWhile(it, e -> cmp(e.getKey(), max, comparator()) <= 0);
                case EXCLUSIVE: return Iterators.takeWhile(it, e -> cmp(e.getKey(), max, comparator()) <  0);
                default: throw new IllegalStateException();
            }
        });
    }

    @Override
    public Set<Entry<K, V>> descendingEntrySet() {
        return new MapEntrySet<K, V>(this, () -> {
            final Iterator<Entry<K, V>> it;
            switch (maxBound) {
                case MISSING:   it = that.lastIterator(); break;
                case INCLUSIVE: it = that.floorIterator(max); break;
                case EXCLUSIVE: it = that.lowerIterator(max); break;
                default: throw new IllegalStateException();
            }

            switch (minBound) {
                case MISSING:   return it;
                case INCLUSIVE: return Iterators.takeWhile(it, e -> cmp(e.getKey(), min, comparator()) >= 0);
                case EXCLUSIVE: return Iterators.takeWhile(it, e -> cmp(e.getKey(), min, comparator()) >  0);
                default: throw new IllegalStateException();
            }
        });
    }
}
