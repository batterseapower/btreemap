package uk.co.omegaprime.btree;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

class Iterables {
    private Iterables() {}

    public static String toString(Iterable xs) {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');

        final Iterator it = xs.iterator();
        if (it.hasNext()) {
            sb.append(it.next());

            while (it.hasNext()) {
                sb.append(',').append(' ');
                sb.append(it.next());
            }
        }

        sb.append(']');
        return sb.toString();
    }

    public static <K, V> String toMapString(Iterable<Map.Entry<K, V>> xs) {
        final StringBuilder sb = new StringBuilder();
        sb.append('{');

        final Iterator<Map.Entry<K, V>> it = xs.iterator();
        if (it.hasNext()) {
            {
                final Map.Entry e = it.next();
                sb.append(e.getKey());
                sb.append('=');
                sb.append(e.getValue());
            }

            while (it.hasNext()) {
                sb.append(',').append(' ');
                {
                    final Map.Entry e = it.next();
                    sb.append(e.getKey());
                    sb.append('=');
                    sb.append(e.getValue());
                }
            }
        }

        sb.append('}');
        return sb.toString();
    }

    public static boolean equals(Iterable xs, Iterable ys) {
        final Iterator thisIt = xs.iterator();
        final Iterator thatIt = ys.iterator();

        while (thisIt.hasNext() && thatIt.hasNext()) {
            final Object thisK = thisIt.next();
            final Object thatK = thatIt.next();

            if (!Objects.equals(thisK, thatK)) {
                return false;
            }
        }

        if (thisIt.hasNext() || thatIt.hasNext()) {
            return false;
        }

        return true;
    }

    public static int hashCode(Iterable xs) {
        int h = 0;
        for (Object k : xs) {
            h += Objects.hashCode(k);
        }

        return h;
    }
}
