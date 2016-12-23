package uk.co.omegaprime.btreemap;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;
import java.util.*;

// This is basically a fixed size Object[] with a "int" size field
final class Node<$K$,$V$> extends AbstractNode {
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
    //
    // After some benchmarking I found that MIN_FANOUT = 16 is ~10% faster than MIN_FANOUT = 8:
    //
    // Min Fanout  Get Throughput (ops/sec)  99.9% CI
    //  4          4216265.491                43520.265
    //  5          4409875.947                91176.223
    //  6          4573909.740                59522.347
    //  7          4833759.229               102045.678
    //  8          4919145.622                55779.310
    //  9          4983996.875               116098.733
    // 10          4727779.650               100560.027
    // 11          4903133.847               180600.028
    // 12          5245384.941                48753.377
    // 13          5248156.906                83901.512
    // 14          4953773.754                77839.875
    // 15          5204504.977               174990.439
    // 16          5305792.167               155854.297
    // 17          5347054.930                47659.022
    // 18          5415975.463                53697.353
    // 19          5398902.319                47398.619
    // 20          5374494.509                73927.102
    // 21          5132999.986                40521.262
    // 22          5161704.152                70647.085
    public static final int MIN_FANOUT = 16;
    public static final int MAX_FANOUT = 2 * MIN_FANOUT - 1;

    // Linear search seems about ~20% faster than binary (for MIN_FANOUT = 8 at least).
    // It's 45% (!) faster for MIN_FANOUT = 16. Crazy.
    public static final boolean BINARY_SEARCH = false;

    private static final Unsafe UNSAFE;
    private static final long KEY_OFFSET0, VALUE_OFFSET0;
    private static final int KEY_SIZE, VALUE_SIZE;

    private static long verifyInstanceFieldsContiguous(String prefix, long stride) {
        final TreeMap<Long, Field> fieldByOffset = new TreeMap<>();
        for (Field f : Node<$K$,$V$>.class.getDeclaredFields()) {
            if (f.getName().matches(Pattern.quote(prefix) + "[0-9]+") && (f.getModifiers() & Modifier.STATIC) == 0) {
                final long offset = UNSAFE.objectFieldOffset(f);
                if (fieldByOffset.put(offset, f) != null) {
                    throw new IllegalStateException("Multiple fields seem to share a single offset " + offset);
                }
            }
        }

        if (fieldByOffset.size() != MAX_FANOUT) {
            throw new IllegalStateException("Expected " + MAX_FANOUT + " " + prefix + " fields, got " + fieldByOffset.size());
        }

        final Iterator<Map.Entry<Long, Field>> it = fieldByOffset.entrySet().iterator();
        final long firstOffset = it.next().getKey();
        long lastOffset = firstOffset;
        while (it.hasNext()) {
            final Map.Entry<Long, Field> e = it.next();
            final long offset = e.getKey();
            if (offset != lastOffset + stride) {
                throw new IllegalStateException("Expected fields to be contiguous in memory but " + e.getValue() + " is at " + offset + " and the last one was at " + lastOffset);
            }

            lastOffset = offset;
        }

        return firstOffset;
    }

    static {
        try {
            Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
            unsafeConstructor.setAccessible(true);
            UNSAFE = unsafeConstructor.newInstance();
        } catch (NoSuchElementException | IllegalAccessException | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        // Object pointer size might differ from Unsafe.ADDRESS_SIZE if compressed OOPs are in use
        KEY_SIZE   = UNSAFE.arrayIndexScale(@Erased $K$[].class);
        VALUE_SIZE = UNSAFE.arrayIndexScale(@Erased $V$[].class);

        KEY_OFFSET0   = verifyInstanceFieldsContiguous("k", KEY_SIZE);
        VALUE_OFFSET0 = verifyInstanceFieldsContiguous("v", VALUE_SIZE);
    }

    private $K$
            k00, k01, k02, k03, k04, k05, k06, k07,
            k08, k09, k10, k11, k12, k13, k14, k15,
            k16, k17, k18, k19, k20, k21, k22, k23,
            k24, k25, k26, k27, k28, k29, k30;
    private $V$
            v00, v01, v02, v03, v04, v05, v06, v07,
            v08, v09, v10, v11, v12, v13, v14, v15,
            v16, v17, v18, v19, v20, v21, v22, v23,
            v24, v25, v26, v27, v28, v29, v30;

    public $K$ getKey  (int i) {
        return ({{K.boxed}})UNSAFE.get{{K.name}}(this, KEY_OFFSET0   + i * KEY_SIZE);
    }
    public $V$ getValue(int i) {
        return ({{V.boxed}})UNSAFE.get{{V.name}}(this, VALUE_OFFSET0 + i * VALUE_SIZE);
    }

    public void setKey  (int i, $K$ x) {
        UNSAFE.put{{K.name}}(this, KEY_OFFSET0   + i * KEY_SIZE, x);
    }
    public void setValue(int i, $V$ x) {
        UNSAFE.put{{V.name}}(this, VALUE_OFFSET0 + i * VALUE_SIZE, x);
    }

    public int binarySearch(int fromIndex, int toIndex, @Erased $K$ key, {{K_}}Comparator c) {
        if (c == null) {
            return binarySearch(fromIndex, toIndex, key);
        }

        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            $K$ midVal = getKey(mid);
            int cmp = c.compare{{K_}}(midVal, key);
            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    public int binarySearch(int fromIndex, int toIndex, @Erased $K$ key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            @SuppressWarnings("rawtypes")
            Comparable midVal = (Comparable)this.getKey(mid);
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

    public static <$K$,$V$> void arraycopyKey(Node<$K$,$V$> src, int srcIndex, Node<? super $K$,?> dst, int dstIndex, int size) {
        if (size < 0 || srcIndex + size > MAX_FANOUT || dstIndex + size > MAX_FANOUT) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (dst == src && srcIndex < dstIndex) {
            for (int i = size - 1; i >= 0; i--) {
                dst.setKey(dstIndex + i, src.getKey(srcIndex + i));
            }
        } else {
            for (int i = 0; i < size; i++) {
                dst.setKey(dstIndex + i, src.getKey(srcIndex + i));
            }
        }
    }

    public static <$K$,$V$> void arraycopyValue(Node<$K$,$V$> src, int srcIndex, Node<?,? super $V$> dst, int dstIndex, int size) {
        if (size < 0 || srcIndex + size > MAX_FANOUT || dstIndex + size > MAX_FANOUT) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (dst == src && srcIndex < dstIndex) {
            for (int i = size - 1; i >= 0; i--) {
                dst.setValue(dstIndex + i, src.getValue(srcIndex + i));
            }
        } else {
            for (int i = 0; i < size; i++) {
                dst.setValue(dstIndex + i, src.getValue(srcIndex + i));
            }
        }
    }
}
