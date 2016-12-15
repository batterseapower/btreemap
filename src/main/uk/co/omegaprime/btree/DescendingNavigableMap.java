package uk.co.omegaprime.btree;

import java.util.*;

class DescendingNavigableMap<K, V> implements NavigableMap<K, V> {
    private final NavigableMap2<K, V> that;

    public DescendingNavigableMap(NavigableMap2<K, V> that) {
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
    public Entry<K, V> lowerEntry(K key) {
        return that.higherEntry(key);
    }

    @Override
    public K lowerKey(K key) {
        return that.higherKey(key);
    }

    @Override
    public Entry<K, V> floorEntry(K key) {
        return that.ceilingEntry(key);
    }

    @Override
    public K floorKey(K key) {
        return that.ceilingKey(key);
    }

    @Override
    public Entry<K, V> ceilingEntry(K key) {
        return that.floorEntry(key);
    }

    @Override
    public K ceilingKey(K key) {
        return that.floorKey(key);
    }

    @Override
    public Entry<K, V> higherEntry(K key) {
        return that.lowerEntry(key);
    }

    @Override
    public K higherKey(K key) {
        return that.lowerKey(key);
    }

    @Override
    public Entry<K, V> firstEntry() {
        return that.lastEntry();
    }

    @Override
    public Entry<K, V> lastEntry() {
        return that.firstEntry();
    }

    @Override
    public Entry<K, V> pollFirstEntry() {
        return that.pollLastEntry();
    }

    @Override
    public Entry<K, V> pollLastEntry() {
        return that.pollFirstEntry();
    }

    @Override
    public NavigableMap<K, V> descendingMap() {
        return that;
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        return new NavigableMapKeySet<K>(this);
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        return that.navigableKeySet();
    }

    @Override
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return new DescendingNavigableMap<K, V>(that.subMap(toKey, toInclusive, fromKey, fromInclusive));
    }

    @Override
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return new DescendingNavigableMap<K, V>(that.tailMap(toKey, inclusive));
    }

    @Override
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return new DescendingNavigableMap<K, V>(that.headMap(fromKey, inclusive));
    }

    @Override
    public Comparator<? super K> comparator() {
        return Collections.reverseOrder(that.comparator());
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
        return that.lastKey();
    }

    @Override
    public K lastKey() {
        return that.firstKey();
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
    public boolean containsKey(Object key) {
        return that.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return that.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return that.get(key);
    }

    @Override
    public V put(K key, V value) {
        return that.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return that.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        that.putAll(m);
    }

    @Override
    public void clear() {
        that.clear();
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
        return that.descendingEntrySet();
    }
}
