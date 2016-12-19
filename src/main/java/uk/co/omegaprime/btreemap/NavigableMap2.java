package uk.co.omegaprime.btreemap;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * Why not just have this extend NavigableMap2 and implement the interface on BTreeMap and friends?
 * Because then this interface would be visible in the public interface of the library.
 */
interface NavigableMap2<K, V> {
    NavigableMap<K, V> asNavigableMap();

    Set<Map.Entry<K, V>> descendingEntrySet();

    NavigableMap2<K,V> subMap(K fromKey, boolean fromInclusive,
                              K toKey,   boolean toInclusive);
    NavigableMap2<K,V> headMap(K toKey,   boolean inclusive);
    NavigableMap2<K,V> tailMap(K fromKey, boolean inclusive);
}
