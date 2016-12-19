package uk.co.omegaprime.btreemap;

import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

class SortedMaps {
    private SortedMaps() {}

    public static <K, V> boolean equals(SortedMap<K, V> xs, Object ys) {
        return ys instanceof Map && equals(xs, (Map)ys);
    }

    private static <K, V> boolean equals(SortedMap<K, V> xs, Map ys) {
        if (xs.size() != ys.size()) return false;

        if (ys instanceof SortedMap && Objects.equals(xs.comparator(), ((SortedMap)ys).comparator())) {
            return Iterables.equals(xs.entrySet(), ys.entrySet());
        } else {
            for (Map.Entry<K, V> e : xs.entrySet()) {
                if (!Objects.equals(ys.get(e.getKey()), e.getValue())) {
                    return false;
                }
            }

            return true;
        }
    }
}
