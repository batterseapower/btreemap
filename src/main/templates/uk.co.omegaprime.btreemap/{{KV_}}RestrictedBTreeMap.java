package uk.co.omegaprime.btreemap;

import java.util.*;

class RestrictedBTreeMap<$K$, $V$> implements NavigableMap<@Boxed $K$, @Boxed $V$> {

    private final BTreeMap<$K$, $V$> that;
    private final @Boxed $K$ min, max;
    private final Bound minBound, maxBound;

    public {{KV_}}RestrictedBTreeMap(BTreeMap<$K$, $V$> that, @Boxed $K$ min, @Boxed $K$ max, Bound minBound, Bound maxBound) {
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
    public Entry<@Boxed $K$, @Boxed $V$> lowerEntry(@Boxed $K$ key) {
        final Entry<@Boxed $K$, @Boxed $V$> e;
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
    public @Boxed $K$ lowerKey(@Boxed $K$ key) {
        return BTreeMap.getEntryKey(lowerEntry(key));
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> floorEntry(@Boxed $K$ key) {
        final Entry<@Boxed $K$, @Boxed $V$> e;
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
    public @Boxed $K$ floorKey(@Boxed $K$ key) {
        return BTreeMap.getEntryKey(floorEntry(key));
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> ceilingEntry(@Boxed $K$ key) {
        final Entry<@Boxed $K$, @Boxed $V$> e;
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
    public @Boxed $K$ ceilingKey(@Boxed $K$ key) {
        return BTreeMap.getEntryKey(ceilingEntry(key));
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> higherEntry(@Boxed $K$ key) {
        final Entry<@Boxed $K$, @Boxed $V$> e;
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
    public @Boxed $K$ higherKey(@Boxed $K$ key) {
        return BTreeMap.getEntryKey(higherEntry(key));
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> firstEntry() {
        switch (minBound) {
            case MISSING:   return that.firstEntry();
            case INCLUSIVE: return that.ceilingEntry(min);
            case EXCLUSIVE: return that.higherEntry(min);
            default: throw new IllegalStateException();
        }
    }

    @Override
    public Entry<@Boxed $K$, @Boxed $V$> lastEntry() {
        switch (minBound) {
            case MISSING:   return that.lastEntry();
            case INCLUSIVE: return that.floorEntry(max);
            case EXCLUSIVE: return that.lowerEntry(max);
            default: throw new IllegalStateException();
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
        return new DescendingNavigableMap<>(this.asNavigableMap2());
    }

    @Override
    public NavigableSet<@Boxed $K$> navigableKeySet() {
        return new NavigableMapKeySet<>(this);
    }

    @Override
    public NavigableSet<@Boxed $K$> descendingKeySet() {
        return descendingMap().descendingKeySet();
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
    public Comparator<? super @Boxed $K$> comparator() {
        return that.comparator();
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

    @Override
    public @Boxed $K$ firstKey() {
        return BTreeMap.getEntryKey(firstEntry());
    }

    @Override
    public @Boxed $K$ lastKey() {
        return BTreeMap.getEntryKey(lastEntry());
    }

    @Override
    public int size() {
        int i = 0;
        for (Map.Entry<@Boxed $K$, @Boxed $V$> _e : this.entrySet()) {
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
    public @Boxed $V$ get(Object key) {
        return inRange(key) ? that.get(key) : null;
    }

    @Override
    public @Boxed $V$ put(@Boxed $K$ key, @Boxed $V$ value) {
        if (!inRange(key)) {
            throw new IllegalArgumentException("key out of range");
        }
        return that.put(key, value);
    }

    @Override
    public @Boxed $V$ remove(Object key) {
        return inRange(key) ? that.remove(key) : null;
    }

    @Override
    public void putAll(Map<? extends @Boxed $K$, ? extends @Boxed $V$> m) {
        for (Map.Entry<? extends @Boxed $K$, ? extends @Boxed $V$> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        // TODO: fast path?
        final Iterator<Entry<@Boxed $K$, @Boxed $V$>> it = entrySet().iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    @Override
    public Set<@Boxed $K$> keySet() {
        return navigableKeySet();
    }

    @Override
    public Collection<@Boxed $V$> values() {
        return new MapValueCollection<@Boxed $V$>(this);
    }

    @Override
    public Set<Entry<@Boxed $K$, @Boxed $V$>> entrySet() {
        return new MapEntrySet<@Boxed $K$, @Boxed $V$>(this, () -> {
            final Iterator<Entry<@Boxed $K$, @Boxed $V$>> it;
            switch (minBound) {
                case MISSING:   it = that.firstIterator(); break;
                case INCLUSIVE: it = that.ceilingIterator(min); break;
                case EXCLUSIVE: it = that.higherIterator(min); break;
                default: throw new IllegalStateException();
            }

            switch (maxBound) {
                case MISSING:   return it;
                case INCLUSIVE: return Iterators.takeWhile(it, e -> Bound.cmp(e.getKey(), max, comparator()) <= 0);
                case EXCLUSIVE: return Iterators.takeWhile(it, e -> Bound.cmp(e.getKey(), max, comparator()) <  0);
                default: throw new IllegalStateException();
            }
        });
    }

    NavigableMap2<@Boxed $K$, @Boxed $V$> asNavigableMap2() {
        return new NavigableMap2<@Boxed $K$, @Boxed $V$>() {
            @Override
            public NavigableMap<@Boxed $K$, @Boxed $V$> asNavigableMap() {
                return {{KV_}}RestrictedBTreeMap.this;
            }

            @Override
            public Set<Entry<@Boxed $K$, @Boxed $V$>> descendingEntrySet() {
                return new MapEntrySet<@Boxed $K$, @Boxed $V$>({{KV_}}RestrictedBTreeMap.this, () -> {
                    final Iterator<Entry<@Boxed $K$, @Boxed $V$>> it;
                    switch (maxBound) {
                        case MISSING:   it = that.lastIterator(); break;
                        case INCLUSIVE: it = that.floorIterator(max); break;
                        case EXCLUSIVE: it = that.lowerIterator(max); break;
                        default: throw new IllegalStateException();
                    }

                    switch (minBound) {
                        case MISSING:   return it;
                        case INCLUSIVE: return Iterators.takeWhile(it, e -> Bound.cmp(e.getKey(), min, comparator()) >= 0);
                        case EXCLUSIVE: return Iterators.takeWhile(it, e -> Bound.cmp(e.getKey(), min, comparator()) >  0);
                        default: throw new IllegalStateException();
                    }
                });

            }

            @Override
            public NavigableMap2<@Boxed $K$, @Boxed $V$> subMap(@Boxed $K$ fromKey, boolean fromInclusive, @Boxed $K$ toKey, boolean toInclusive) {
                // FIXME: javadoc specifies several sanity checks that should generate a IllegalArgumentException. May need some of these on BTreeMap too. (or in our constructor)
                return headMap(toKey, toInclusive).tailMap(fromKey, fromInclusive);
            }

            @Override
            public NavigableMap2<@Boxed $K$, @Boxed $V$> headMap(@Boxed $K$ toKey, boolean inclusive) {
                if (maxBound.lt(toKey, max, comparator())) {
                    return new RestrictedBTreeMap<$K$, $V$>(that, min, toKey, minBound, Bound.inclusive(inclusive)).asNavigableMap2();
                } else {
                    return this;
                }
            }

            @Override
            public NavigableMap2<@Boxed $K$, @Boxed $V$> tailMap(@Boxed $K$ fromKey, boolean inclusive) {
                if (minBound.lt(min, fromKey, comparator())) {
                    return new RestrictedBTreeMap<$K$, $V$>(that, fromKey, max, Bound.inclusive(inclusive), maxBound).asNavigableMap2();
                } else {
                    return this;
                }
            }
        };
    }
}
