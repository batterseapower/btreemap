package uk.co.omegaprime.btreemap;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

// This is basically a fixed size Object[] with a "int" size field
final class Node {
    // We're going to do a generalized (2, 3) tree i.e. each internal node will have between m and (2m - 1) children inclusive, for m >= 2
    //
    // What's a sensible value for m? It would be good if each array we allocate fits within one cache line. On Skylake,
    // cache lines are 64 bytes, and with compressed OOPS (default for heap sizes < 32GB) object pointers are only 4 bytes long,
    // implying that MAX_FANOUT = 16 would be a good choice, i.e. MIN_FANOUT = 8.
    //
    // With MIN_FANOUT=2:
    //   Benchmark               Mode  Cnt        Score        Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  1900386.806 ± 115791.569  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  1617089.096 ±  32891.292  ops/s
    //
    // With MIN_FANOUT=8:
    //   Benchmark               Mode  Cnt        Score        Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  4021130.733 ±  31473.315  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  2821784.716 ± 141837.270  ops/s
    //
    // java.util.TreeMap:
    //   Benchmark               Mode  Cnt        Score        Error  Units
    //   BTreeMapBenchmark.get  thrpt   40  3226633.131 ± 195725.464  ops/s
    //   BTreeMapBenchmark.put  thrpt   40  2561772.533 ±  31611.667  ops/s
    public static final int MIN_FANOUT = 8;
    public static final int MAX_FANOUT = 2 * MIN_FANOUT - 1;

    // Linear search seems about ~20% faster than binary (for MIN_FANOUT = 8 at least)
    public static final boolean BINARY_SEARCH = false;

    private static final Unsafe UNSAFE;
    private static final long OFFSET0;
    private static final int POINTER_SIZE;

    static {
        try {
            Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
            unsafeConstructor.setAccessible(true);
            UNSAFE = unsafeConstructor.newInstance();
        } catch (NoSuchElementException | IllegalAccessException | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        final TreeMap<Long, Field> fieldByOffset = new TreeMap<>();
        for (Field f : Node.class.getDeclaredFields()) {
            if (Object.class.isAssignableFrom(f.getType()) && (f.getModifiers() & Modifier.STATIC) == 0) {
                final long offset = UNSAFE.objectFieldOffset(f);
                if (fieldByOffset.put(offset, f) != null) {
                    throw new IllegalStateException("Multiple fields seem to share a single offset " + offset);
                }
            }
        }

        if (fieldByOffset.size() != MAX_FANOUT*2) {
            throw new IllegalStateException("Expected " + (MAX_FANOUT * 2) + " object fields, got " + fieldByOffset.size());
        }

        // This might differ from ADDRESS_SIZE if compressed OOPs are in use
        POINTER_SIZE = UNSAFE.arrayIndexScale(Object[].class);

        final Iterator<Map.Entry<Long, Field>> it = fieldByOffset.entrySet().iterator();
        long lastOffset = OFFSET0 = it.next().getKey();
        while (it.hasNext()) {
            final Map.Entry<Long, Field> e = it.next();
            final long offset = e.getKey();
            if (offset != lastOffset + POINTER_SIZE) {
                throw new IllegalStateException("Expected object fields to be contiguous in memory but " + e.getValue() + " is at " + offset + " and the last one was at " + lastOffset);
            }

            lastOffset = offset;
        }
    }

    public int size;
    private Object
            o00, o01, o02, o03, o04, o05, o06, o07,
            o08, o09, o10, o11, o12, o13, o14, o15,
            o16, o17, o18, o19, o20, o21, o22, o23,
            o24, o25, o26, o27, o28, o29;

    public Object get(int i) {
        return UNSAFE.getObject(this, OFFSET0 + i * POINTER_SIZE);
    }

    public void set(int i, Object x) {
        UNSAFE.putObject(this, OFFSET0 + i * POINTER_SIZE, x);
    }

    public int binarySearch(int fromIndex, int toIndex, Object key, Comparator c) {
        if (c == null) {
            return binarySearch(fromIndex, toIndex, key);
        }

        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Object midVal = get(mid);
            int cmp = c.compare(midVal, key);
            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    public int binarySearch(int fromIndex, int toIndex, Object key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            @SuppressWarnings("rawtypes")
            Comparable midVal = (Comparable)this.get(mid);
            @SuppressWarnings("unchecked")
            int cmp = midVal.compareTo(key);

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    public static void arraycopy(Node src, int srcIndex, Node dst, int dstIndex, int size) {
        if (size < 0 || srcIndex + size > MAX_FANOUT*2 || dstIndex + size > MAX_FANOUT*2) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (dst == src && srcIndex < dstIndex) {
            for (int i = size - 1; i >= 0; i--) {
                dst.set(dstIndex + i, src.get(srcIndex + i));
            }
        } else {
            for (int i = 0; i < size; i++) {
                dst.set(dstIndex + i, src.get(srcIndex + i));
            }
        }
    }
}
