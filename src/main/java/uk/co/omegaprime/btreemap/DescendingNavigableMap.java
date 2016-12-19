package uk.co.omegaprime.btreemap;

import java.util.*;

class DescendingNavigableMap<K, V> implements NavigableMap<K, V> {
    private final NavigableMap2<K, V> that;

    public DescendingNavigableMap(NavigableMap2<K, V> that) {
        this.that = that;
    }

    @Override
    public String toString() {
        return Iterables.toMapString(this.entrySet());
    }

    @Override
    public boolean equals(Object that) {
        return SortedMaps.equals(this, that);
    }

    @Override
    public int hashCode() {
        return Iterables.hashCode(entrySet());
    }

    @Override
    public Entry<K, V> lowerEntry(K key) {
        return that.asNavigableMap().higherEntry(key);
    }

    @Override
    public K lowerKey(K key) {
        return that.asNavigableMap().higherKey(key);
    }

    @Override
    public Entry<K, V> floorEntry(K key) {
        return that.asNavigableMap().ceilingEntry(key);
    }

    @Override
    public K floorKey(K key) {
        return that.asNavigableMap().ceilingKey(key);
    }

    @Override
    public Entry<K, V> ceilingEntry(K key) {
        return that.asNavigableMap().floorEntry(key);
    }

    @Override
    public K ceilingKey(K key) {
        return that.asNavigableMap().floorKey(key);
    }

    @Override
    public Entry<K, V> higherEntry(K key) {
        return that.asNavigableMap().lowerEntry(key);
    }

    @Override
    public K higherKey(K key) {
        return that.asNavigableMap().lowerKey(key);
    }

    @Override
    public Entry<K, V> firstEntry() {
        return that.asNavigableMap().lastEntry();
    }

    @Override
    public Entry<K, V> lastEntry() {
        return that.asNavigableMap().firstEntry();
    }

    @Override
    public Entry<K, V> pollFirstEntry() {
        return that.asNavigableMap().pollLastEntry();
    }

    @Override
    public Entry<K, V> pollLastEntry() {
        return that.asNavigableMap().pollFirstEntry();
    }

    @Override
    public NavigableMap<K, V> descendingMap() {
        return that.asNavigableMap();
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        return new NavigableMapKeySet<K>(this);
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        return that.asNavigableMap().navigableKeySet();
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
        return Collections.reverseOrder(that.asNavigableMap().comparator());
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
        return that.asNavigableMap().lastKey();
    }

    @Override
    public K lastKey() {
        return that.asNavigableMap().firstKey();
    }

    @Override
    public int size() {
        return that.asNavigableMap().size();
    }

    @Override
    public boolean isEmpty() {
        return that.asNavigableMap().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return that.asNavigableMap().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return that.asNavigableMap().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return that.asNavigableMap().get(key);
    }

    @Override
    public V put(K key, V value) {
        return that.asNavigableMap().put(key, value);
    }

    @Override
    public V remove(Object key) {
        return that.asNavigableMap().remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        that.asNavigableMap().putAll(m);
    }

    @Override
    public void clear() {
        that.asNavigableMap().clear();
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
