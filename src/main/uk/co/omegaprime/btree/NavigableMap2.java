package uk.co.omegaprime.btree;

import java.util.NavigableMap;
import java.util.Set;

interface NavigableMap2<K, V> extends NavigableMap<K, V> {
    Set<Entry<K, V>> descendingEntrySet();

    NavigableMap2<K,V> subMap(K fromKey, boolean fromInclusive,
                              K toKey,   boolean toInclusive);
    NavigableMap2<K,V> headMap(K toKey,   boolean inclusive);
    NavigableMap2<K,V> tailMap(K fromKey, boolean inclusive);
}
