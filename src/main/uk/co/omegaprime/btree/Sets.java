package uk.co.omegaprime.btree;

import java.util.Set;

class Sets {
    private Sets() {}

    public static <K> boolean equals(Set<K> xs, Object ys) {
        return ys instanceof Set && equals(xs, (Set)ys);
    }

    public static <K> boolean equals(Set<K> xs, Set ys) {
        if (xs.size() != ys.size()) return false;

        for (K k : xs) {
            if (!ys.contains(k)) {
                return false;
            }
        }

        return true;
    }
}
