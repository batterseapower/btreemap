package uk.co.omegaprime.btree;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

class Iterators {
    private Iterators() {}

    public static <T> Iterator<T> takeWhile(Iterator<T> it, Predicate<T> p) {
        return new Iterator<T>() {
            private Boolean hasNext;
            private T next;

            private boolean ensureHasNext() {
                if (hasNext != null) {
                    return hasNext;
                }

                if (!it.hasNext()) {
                    return hasNext = false;
                }

                next = it.next();
                return hasNext = p.test(next);
            }

            @Override
            public boolean hasNext() {
                return ensureHasNext();
            }

            @Override
            public T next() {
                if (!ensureHasNext()) {
                    throw new NoSuchElementException("Iterator exhausted");
                }

                final T result = next;

                hasNext = null;
                next = null; // Just to free up memory
                return result;
            }
        };
    }
}
