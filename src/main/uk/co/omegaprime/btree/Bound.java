package uk.co.omegaprime.btree;

import java.util.Comparator;

enum Bound {
    MISSING, INCLUSIVE, EXCLUSIVE;

    public static Bound inclusive(boolean flag) {
        return flag ? INCLUSIVE : EXCLUSIVE;
    }

    public static int cmp(Object l, Object r, Comparator c) {
        return c == null ? ((Comparable)l).compareTo(r) : c.compare(l, r);
    }

    public boolean lt(Object x, Object y, Comparator c) {
        if (this == MISSING) return true;

        final int r = cmp(x, y, c);
        return r < 0 || (r == 0 && this == INCLUSIVE);
    }
}
